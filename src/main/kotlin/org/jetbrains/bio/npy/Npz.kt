package org.jetbrains.bio.npy

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.min

/**
 * A ZIP file where each individual file is in NPY format.
 *
 * By convention each file has `.npy` extension, however, the API
 * doesn't expose it. So for instance the array named "X" will be
 * accessibly via "X" and **not** "X.npy".
 *
 * See https://docs.scipy.org/doc/numpy-dev/neps/npy-format.html
 */
object NpzFile {
    /**
     * A reader for NPZ format.
     *
     * @since 0.2.0
     */
    data class Reader internal constructor(private val path: Path) : Closeable, AutoCloseable {
        private val zf = ZipFile(path.toFile(), ZipFile.OPEN_READ, Charsets.US_ASCII)

        /**
         * Returns a mapping from array names to the corresponding
         * scalar types in Java.
         *
         * @since 0.2.0
         */
        fun introspect() = zf.entries().asSequence().map {
            val header = NpyFile.Header.read(zf.getBuffers(it, Integer.MAX_VALUE).first())
            val type = when (header.type) {
                'b' -> when (header.bytes) {
                    1 -> Boolean::class.java
                    else -> unexpectedByteNumber(header.type, header.bytes)
                }
                'i', 'u' -> when (header.bytes) {
                    1 -> Byte::class.java
                    2 -> Short::class.java
                    4 -> Int::class.java
                    8 -> Long::class.java
                    else -> unexpectedByteNumber(header.type, header.bytes)
                }
                'f' -> when (header.bytes) {
                    4 -> Float::class.java
                    8 -> Double::class.java
                    else -> unexpectedByteNumber(header.type, header.bytes)
                }
                'S' -> String::class.java
                else -> unexpectedHeaderType(header.type)
            }

            NpzEntry(it.name.substringBeforeLast('.'), type, header.shape)
        }.toList()

        /**
         * Returns an array for a given name.
         *
         * The caller is responsible for casting the resulting array to an
         * appropriate type.
         *
         * @param name array name.
         * @param step amount of bytes to use for the temporary buffer, when
         *             reading the entry. Defaults to 1 << 18 to mimic NumPy
         *             behaviour.
         */
        operator fun get(name: String, step: Int = 1 shl 18): NpyArray {
            return NpyFile.read(zf.getBuffers(zf.getEntry("$name.npy"), step))
        }

        private fun ZipFile.getBuffers(entry: ZipEntry, step: Int): Sequence<ByteBuffer> {
            val `is` = getInputStream(entry)
            var chunk = ByteBuffer.allocate(0)
            return generateSequence {
                val remaining = `is`.available() + chunk.remaining()
                if (remaining > 0) {
                    chunk = ByteBuffer.allocate(min(remaining, step)).apply {
                        // Make sure we don't miss any unaligned bytes.
                        put(chunk)
                        Channels.newChannel(`is`).read(this)
                        rewind()
                    }.asReadOnlyBuffer()

                    chunk
                } else {
                    `is`.close()
                    null
                }
            }
        }

        override fun close() = zf.close()
    }

    /** Opens an NPZ file at [path] for reading. */
    @JvmStatic
    fun read(path: Path) = Reader(path)

    /**
     * A reader for NPZ format backed by [ZipInputStream].
     *
     * Since [ZipInputStream] is forward-only, this reader buffers arrays in memory
     * on first access. To support repeated reads from a network stream, callers can
     * first cache bytes and then wrap them with [ByteArrayInputStream].
     */
    class StreamReader internal constructor(private val zis: ZipInputStream) : Closeable, AutoCloseable {
        private var arrays: Map<String, NpyArray>? = null

        /**
         * Returns a mapping from array names to the corresponding
         * scalar types in Java.
         */
        fun introspect(): List<NpzEntry> {
            return readAll().map { (name, array) ->
                NpzEntry(name, toScalarType(array), array.shape)
            }
        }

        /**
         * Returns an array for a given name.
         *
         * The caller is responsible for casting the resulting array to an
         * appropriate type.
         */
        operator fun get(name: String): NpyArray {
            return readAll()[name] ?: throw IllegalArgumentException("entry not found: $name")
        }

        private fun readAll(): Map<String, NpyArray> {
            arrays?.let { return it }

            val loaded = linkedMapOf<String, NpyArray>()
            val chunk = ByteArray(1 shl 18)
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val bos = ByteArrayOutputStream()
                    while (true) {
                        val n = zis.read(chunk)
                        if (n < 0) {
                            break
                        }
                        bos.write(chunk, 0, n)
                    }

                    val name = entry.name.substringBeforeLast('.')
                    loaded[name] = NpyFile.read(ByteArrayInputStream(bos.toByteArray()))
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }

            arrays = loaded
            return loaded
        }

        private fun toScalarType(array: NpyArray): Class<*> = when (array.data) {
            is BooleanArray -> Boolean::class.java
            is ByteArray -> Byte::class.java
            is ShortArray -> Short::class.java
            is IntArray -> Int::class.java
            is LongArray -> Long::class.java
            is FloatArray -> Float::class.java
            is DoubleArray -> Double::class.java
            is Array<*> -> String::class.java
            else -> error("unsupported array data type: ${array.data::class.java}")
        }

        override fun close() = zis.close()
    }

    /** Opens an NPZ stream for reading. */
    @JvmStatic
    fun read(input: InputStream): StreamReader {
        return StreamReader(ZipInputStream(input, Charsets.US_ASCII))
    }

    /**
     * A writer for NPZ format.
     *
     * The implementation uses a temporary [ByteBuffer] to store the
     * serialized array prior to archiving. Thus each [write] call
     * requires N extra bytes of memory for an array of N bytes.
     */
    data class Writer internal constructor(private val zos: ZipOutputStream, private val compressed: Boolean) :
        Closeable, AutoCloseable {

        @JvmOverloads
        fun write(name: String, data: BooleanArray, shape: IntArray = intArrayOf(data.size)) {
            writeEntry(name, NpyFile.allocate(data, shape))
        }

        @JvmOverloads
        fun write(name: String, data: ByteArray, shape: IntArray = intArrayOf(data.size)) {
            writeEntry(name, NpyFile.allocate(data, shape))
        }

        @JvmOverloads
        fun write(
            name: String,
            data: ShortArray,
            shape: IntArray = intArrayOf(data.size),
            order: ByteOrder = ByteOrder.nativeOrder()
        ) {
            writeEntry(name, NpyFile.allocate(data, shape, order))
        }

        @JvmOverloads
        fun write(
            name: String,
            data: IntArray,
            shape: IntArray = intArrayOf(data.size),
            order: ByteOrder = ByteOrder.nativeOrder()
        ) {
            writeEntry(name, NpyFile.allocate(data, shape, order))
        }

        @JvmOverloads
        fun write(
            name: String,
            data: LongArray,
            shape: IntArray = intArrayOf(data.size),
            order: ByteOrder = ByteOrder.nativeOrder()
        ) {
            writeEntry(name, NpyFile.allocate(data, shape, order))
        }

        @JvmOverloads
        fun write(
            name: String,
            data: FloatArray,
            shape: IntArray = intArrayOf(data.size),
            order: ByteOrder = ByteOrder.nativeOrder()
        ) {
            writeEntry(name, NpyFile.allocate(data, shape, order))
        }

        @JvmOverloads
        fun write(
            name: String,
            data: DoubleArray,
            shape: IntArray = intArrayOf(data.size),
            order: ByteOrder = ByteOrder.nativeOrder()
        ) {
            writeEntry(name, NpyFile.allocate(data, shape, order))
        }

        @JvmOverloads
        fun write(
            name: String,
            data: Array<String>,
            shape: IntArray = intArrayOf(data.size)
        ) {
            writeEntry(name, NpyFile.allocate(data, shape))
        }

        private fun writeEntry(name: String, chunks: Sequence<ByteBuffer>) {
            val entry = ZipEntry("$name.npy").apply {
                if (compressed) {
                    method = ZipEntry.DEFLATED
                } else {
                    method = ZipEntry.STORED

                    val crcAcc = CRC32()
                    var sizeAcc = 0L
                    for (chunk in chunks) {
                        sizeAcc += chunk.capacity().toLong()
                        crcAcc.update(chunk)
                        chunk.rewind()
                    }

                    size = sizeAcc
                    crc = crcAcc.value
                }
            }

            zos.putNextEntry(entry)
            try {
                val fc = Channels.newChannel(zos)
                for (chunk in chunks) {
                    while (chunk.hasRemaining()) {
                        fc.write(chunk)
                    }
                }
            } finally {
                zos.closeEntry()
            }
        }

        override fun close() = zos.close()
    }

    /** Opens an NPZ file at [path] for writing. */
    @JvmStatic
    fun write(path: Path, compressed: Boolean = false): Writer {
        val zos = ZipOutputStream(Files.newOutputStream(path).buffered(), Charsets.US_ASCII)
        return Writer(zos, compressed)
    }

    /** Opens an NPZ stream for writing. */
    @JvmStatic
    fun write(output: OutputStream, compressed: Boolean = false): Writer {
        val zos = ZipOutputStream(output.buffered(), Charsets.US_ASCII)
        return Writer(zos, compressed)
    }
}

/** A stripped down NPY header for an array in NPZ. */
class NpzEntry(val name: String, val type: Class<*>, private val shape: IntArray) {
    override fun toString() = StringJoiner(", ", "NpzEntry{", "}")
        .add("name=$name")
        .add("type=$type")
        .add("shape=${shape.contentToString()}")
        .toString()
}