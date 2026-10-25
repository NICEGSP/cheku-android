package com.cheku.app.fit

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FitExportWriterTest {
    @Test
    fun writesFlushesAndClosesOutput() {
        val output = TrackingOutputStream()
        val bytes = byteArrayOf(1, 2, 3)

        assertEquals(3, FitExportWriter.write(bytes) { output })
        assertArrayEquals(bytes, output.delegate.toByteArray())
        assertEquals(true, output.flushed)
        assertEquals(true, output.closed)
    }

    @Test
    fun nullOutputFails() {
        assertThrows(IllegalStateException::class.java) {
            FitExportWriter.write(byteArrayOf(1)) { null }
        }
    }

    @Test
    fun writeFailurePropagatesAndClosesOutput() {
        val output = object : OutputStream() {
            var closed = false
            override fun write(value: Int) = throw IOException("disk full")
            override fun close() { closed = true }
        }

        assertThrows(IOException::class.java) {
            FitExportWriter.write(byteArrayOf(1)) { output }
        }
        assertEquals(true, output.closed)
    }

    private class TrackingOutputStream : OutputStream() {
        val delegate = ByteArrayOutputStream()
        var flushed = false
        var closed = false
        override fun write(value: Int) = delegate.write(value)
        override fun flush() { flushed = true }
        override fun close() { closed = true }
    }
}
