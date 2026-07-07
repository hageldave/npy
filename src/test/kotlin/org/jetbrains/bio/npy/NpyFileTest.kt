package org.jetbrains.bio.npy

import org.junit.Assert.assertArrayEquals
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ProcessBuilder.Redirect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class NpyFileTest(private val order: ByteOrder) {
    @Test fun writeReadBooleans() = withTempFile("test", ".npy") { path ->
        val data = booleanArrayOf(true, true, true, false)
        NpyFile.write(path, data)
        assertArrayEquals(data, NpyFile.read(path).asBooleanArray())
    }

    @Test fun writeReadBytes() = withTempFile("test", ".npy") { path ->
        val data = byteArrayOf(1, 2, 3, 4)
        NpyFile.write(path, data)
        assertArrayEquals(data, NpyFile.read(path).asByteArray())
    }

    @Test fun writeReadShorts() = withTempFile("test", ".npy") { path ->
        val data = shortArrayOf(1, 2, 3, 4)
        NpyFile.write(path, data, order = order)
        assertArrayEquals(data, NpyFile.read(path).asShortArray())
    }

    @Test fun writeReadInts() = withTempFile("test", ".npy") { path ->
        val data = intArrayOf(1, 2, 3, 4)
        NpyFile.write(path, data, order = order)
        assertArrayEquals(data, NpyFile.read(path).asIntArray())
    }

    @Test fun writeReadLongs() = withTempFile("test", ".npy") { path ->
        val data = longArrayOf(1, 2, 3, 4)
        NpyFile.write(path, data, order = order)
        assertArrayEquals(data, NpyFile.read(path).asLongArray())
    }

    @Test fun writeReadFloats() = withTempFile("test", ".npy") { path ->
        val data = floatArrayOf(1f, 2f, 3f, 4f)
        NpyFile.write(path, data, order = order)
        assertArrayEquals(data, NpyFile.read(path).asFloatArray(), Math.ulp(1f))
    }

    @Test fun writeReadDoubles() = withTempFile("test", ".npy") { path ->
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        NpyFile.write(path, data, order = order)
        assertArrayEquals(data, NpyFile.read(path).asDoubleArray(), Math.ulp(1.0))
    }

    @Suppress("unchecked_cast")
    @Test fun writeReadStrings() = withTempFile("test", ".npy") { path ->
        val data = arrayOf("foo", "bar", "bazooka")
        NpyFile.write(path, data)
        assertArrayEquals(data, NpyFile.read(path).asStringArray())
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun `data`(): Collection<Any> = listOf(ByteOrder.BIG_ENDIAN,
                                               ByteOrder.LITTLE_ENDIAN)
    }
}

@RunWith(Parameterized::class)
class NpyFileStreamTest(private val order: ByteOrder) {
    @Test fun writeReadBooleans() {
        val data = booleanArrayOf(true, true, true, false)
        val bos = ByteArrayOutputStream()
        NpyFile.write(bos, data)
        assertArrayEquals(data, NpyFile.read(ByteArrayInputStream(bos.toByteArray())).asBooleanArray())
    }

    @Test fun writeReadBytes() {
        val data = byteArrayOf(1, 2, 3, 4)
        val bos = ByteArrayOutputStream()
        NpyFile.write(bos, data)
        assertArrayEquals(data, NpyFile.read(ByteArrayInputStream(bos.toByteArray())).asByteArray())
    }

    @Test fun writeReadShorts() {
        val data = shortArrayOf(1, 2, 3, 4)
        val bos = ByteArrayOutputStream()
        NpyFile.write(bos, data, order = order)
        assertArrayEquals(data, NpyFile.read(ByteArrayInputStream(bos.toByteArray())).asShortArray())
    }

    @Test fun writeReadInts() {
        val data = intArrayOf(1, 2, 3, 4)
        val bos = ByteArrayOutputStream()
        NpyFile.write(bos, data, order = order)
        assertArrayEquals(data, NpyFile.read(ByteArrayInputStream(bos.toByteArray())).asIntArray())
    }

    @Test fun writeReadLongs() {
        val data = longArrayOf(1, 2, 3, 4)
        val bos = ByteArrayOutputStream()
        NpyFile.write(bos, data, order = order)
        assertArrayEquals(data, NpyFile.read(ByteArrayInputStream(bos.toByteArray())).asLongArray())
    }

    @Test fun writeReadFloats() {
        val data = floatArrayOf(1f, 2f, 3f, 4f)
        val bos = ByteArrayOutputStream()
        NpyFile.write(bos, data, order = order)
        assertArrayEquals(data, NpyFile.read(ByteArrayInputStream(bos.toByteArray())).asFloatArray(), Math.ulp(1f))
    }

    @Test fun writeReadDoubles() {
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        val bos = ByteArrayOutputStream()
        NpyFile.write(bos, data, order = order)
        assertArrayEquals(data, NpyFile.read(ByteArrayInputStream(bos.toByteArray())).asDoubleArray(), Math.ulp(1.0))
    }

    @Suppress("unchecked_cast")
    @Test fun writeReadStrings() {
        val data = arrayOf("foo", "bar", "bazooka")
        val bos = ByteArrayOutputStream()
        NpyFile.write(bos, data)
        assertArrayEquals(data, NpyFile.read(ByteArrayInputStream(bos.toByteArray())).asStringArray())
    }

    @Test fun streamAndFileOutputsMatch() = withTempFile("test", ".npy") { path ->
        val data = intArrayOf(1, 2, 3, 4)
        val bos = ByteArrayOutputStream()
        NpyFile.write(path, data, order = order)
        NpyFile.write(bos, data, order = order)
        assertArrayEquals(Files.readAllBytes(path), bos.toByteArray())
    }

    @Test fun writeFileOutputStreamReadPath() = withTempFile("test", ".npy") { path ->
        val data = intArrayOf(1, 2, 3, 4)
        FileOutputStream(path.toFile()).use { NpyFile.write(it, data, order = order) }
        assertArrayEquals(data, NpyFile.read(path).asIntArray())
    }

    @Test fun writePathReadFileInputStream() = withTempFile("test", ".npy") { path ->
        val data = intArrayOf(1, 2, 3, 4)
        NpyFile.write(path, data, order = order)
        FileInputStream(path.toFile()).use { input ->
            assertArrayEquals(data, NpyFile.read(input).asIntArray())
        }
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun `data`(): Collection<Any> = listOf(ByteOrder.BIG_ENDIAN,
                                               ByteOrder.LITTLE_ENDIAN)
    }
}

@RunWith(Parameterized::class)
class NpyFileNDTest(private val shape: IntArray) {
    @Test fun writeReadDouble() = withTempFile("test", ".npy") { path ->
        val data = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
        NpyFile.write(path, data, shape = shape)
        val serialized = NpyFile.read(path)
        assertArrayEquals(shape, serialized.shape)
        assertArrayEquals(data, serialized.asDoubleArray(), Math.ulp(1.0))
    }

    companion object {
        @JvmStatic @Parameters fun `data`() = listOf(
                intArrayOf(1, 8),
                intArrayOf(8, 1),
                intArrayOf(2, 4),
                intArrayOf(4, 2),
                intArrayOf(2, 2, 2),
                intArrayOf(2, 2, 2, 1))
    }
}

class NpyFileFortranOrderTest {
    @Test fun readIntsReordersFortranDataToCOrder() = withTempFile("test", ".npy") { path ->
        Files.write(
            path,
            fortranNpyBytes(
                shape = intArrayOf(2, 3),
                data = intArrayOf(1, 4, 2, 5, 3, 6),
                order = ByteOrder.LITTLE_ENDIAN
            )
        )

        val array = NpyFile.read(path)
        assertArrayEquals(intArrayOf(2, 3), array.shape)
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6), array.asIntArray())
    }

    @Test fun readStringsReordersFortranDataToCOrder() {
        val input = ByteArrayInputStream(
            fortranNpyBytes(
                shape = intArrayOf(2, 2),
                data = arrayOf("aa", "ccc", "b", "d")
            )
        )

        val array = NpyFile.read(input)
        assertArrayEquals(intArrayOf(2, 2), array.shape)
        assertArrayEquals(arrayOf("aa", "b", "ccc", "d"), array.asStringArray())
    }

    private fun fortranNpyBytes(shape: IntArray, data: IntArray, order: ByteOrder): ByteArray {
        val header = NpyFile.Header(
            order = order,
            type = 'i',
            bytes = Integer.BYTES,
            shape = shape,
            fortranOrder = true
        ).allocate()
        val body = ByteBuffer.allocate(data.size * Integer.BYTES).order(order)
        body.asIntBuffer().put(data)
        return header.toByteArray() + body.array()
    }

    private fun fortranNpyBytes(shape: IntArray, data: Array<String>): ByteArray {
        val bytes = data.maxOfOrNull { it.length } ?: 0
        val header = NpyFile.Header(
            order = null,
            type = 'S',
            bytes = bytes,
            shape = shape,
            fortranOrder = true
        ).allocate()
        val body = ByteBuffer.allocate(data.size * bytes)
        data.forEach { body.put(it.toByteArray(Charsets.US_ASCII).copyOf(bytes)) }
        return header.toByteArray() + body.array()
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val copy = duplicate()
        val bytes = ByteArray(copy.remaining())
        copy.get(bytes)
        return bytes
    }
}

class NpyFileHeaderTest {
    @Test fun isPadded() {
        val header = NpyFile.Header(type = 'i', bytes = 4, shape = intArrayOf(42))
        assertEquals(1, header.major)
        assertTrue(header.allocate().capacity() % 16 == 0)
    }

    @Test fun writeRead10() = testWriteRead(65536)

    @Test fun writeRead20() = testWriteRead(16)  // force 2.0

    private fun testWriteRead(boundary: Int = NpyFile.Header.NPY_10_20_SIZE_BOUNDARY) {
        withTempFile("test", ".npz") { path ->
            val backup = NpyFile.Header.NPY_10_20_SIZE_BOUNDARY
            NpyFile.Header.NPY_10_20_SIZE_BOUNDARY = boundary
            val header = NpyFile.Header(type = 'i', bytes = 4, shape = intArrayOf(0))
            NpyFile.Header.NPY_10_20_SIZE_BOUNDARY = backup

            FileChannel.open(path, StandardOpenOption.WRITE).use {
                val output = header.allocate()
                output.rewind()
                it.write(output)
            }

            FileChannel.open(path).use {
                val input = it.map(FileChannel.MapMode.READ_ONLY, 0, Files.size(path))
                assertEquals(header, NpyFile.Header.read(input))
            }
        }
    }
}

class NpyFileNumPyTest {
    private val hasNumPy: Boolean get() = try {
        val (rc, _) = command("python", "-c", "import numpy")
        rc == 0
    } catch(e: IOException) {
        if (e.message?.startsWith("Cannot run program \"python\"") != true) {
            throw e
        }
        // no python installed
        false
    }

    @Test fun writeRead() {
        Assume.assumeTrue(hasNumPy)

        withTempFile("test", ".npy") { path ->
            val data = intArrayOf(1, 2, 3, 4)
            NpyFile.write(path, data)
            val (rc, output) = command(
                    "python", "-c", "import numpy as np; print(np.load('$path'))")
            assertEquals(0, rc)
            assertEquals("[1 2 3 4]", output.trim())
        }
    }

    private fun command(vararg args: String): Pair<Int, String> {
        val p = ProcessBuilder()
                .command(*args)
                .redirectOutput(Redirect.PIPE)
                .start()

        val rc = p.waitFor()
        return rc to p.inputStream.bufferedReader().readText()
    }
}

class NpyFileStressTest {
    @Test(expected = IllegalStateException::class)
    fun readRandomGibberish() = withTempFile("test", ".npy") { path ->
        val r = Random()
        Files.write(path, ByteArray(65536) { r.nextInt().toByte() })
        NpyFile.read(path)
    }

    @Test fun writeReadRandom() {
        val r = Random()
        val maxMemory = Runtime.getRuntime().maxMemory() / java.lang.Double.BYTES
        val maxSize = Math.toIntExact(maxMemory / 100)  // 1%
        for (i in 0 until 10) {
            val size = r.nextInt(maxSize).toLong()
            val data = r.doubles(size).toArray()

            withTempFile("test", ".npy") { path ->
                NpyFile.write(path, data)

                assertArrayEquals(data, NpyFile.read(path).asDoubleArray(),
                                  Math.ulp(1.0))
            }
        }
    }

    @Test fun writeReadUnaligned() = withTempFile("test", ".npy") { path ->
        val data = Random().doubles(65536).toArray()
        NpyFile.write(path, data)
        assertArrayEquals(data, NpyFile.read(path, step=123).asDoubleArray(),
                          Math.ulp(1.0))
    }
}

class NpyFileNonRegressionTest {
    @Test fun readLongShapePython2() {
        // See https://github.com/JetBrains-Research/npy/pull/6.
        assertArrayEquals(
                doubleArrayOf(1.0, 1.0),
                NpyFile.read(Examples["win64python2.npy"]).asDoubleArray(),
                Math.ulp(1.0))
    }
}

class NpyFileSizeOverflowTest {
    @Test(expected = IllegalStateException::class)
    fun readSizeOverflow() {
        NpyFile.read(Examples["overflow.npy"])
    }
}