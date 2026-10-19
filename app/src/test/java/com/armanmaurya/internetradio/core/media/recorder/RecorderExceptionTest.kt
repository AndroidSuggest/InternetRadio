package com.armanmaurya.internetradio.core.media.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class RecorderExceptionTest {

    @Test
    fun streamReadException_containsCorrectMessageAndCause() {
        val cause = SocketTimeoutException("Read timed out")
        val exception = RecorderException.StreamReadException("Stream recording error", cause)

        assertTrue(exception.message?.contains("Stream read error: Stream recording error") == true)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun sinkWriteException_containsCorrectMessageAndCause() {
        val cause = java.io.IOException("Disk full")
        val exception = RecorderException.SinkWriteException("Failed to write to storage", cause)

        assertTrue(exception.message?.contains("Storage sink write error: Failed to write to storage") == true)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun allRecorderExceptions_areSubclassesOfRecorderException() {
        val readEx: Any = RecorderException.StreamReadException("Read failed")
        val writeEx: Any = RecorderException.SinkWriteException("Write failed")
        val connEx: Any = RecorderException.StreamConnectionException("http://example.com")
        val parseEx: Any = RecorderException.PlaylistParseException("Parse failed")

        assertTrue(readEx is RecorderException)
        assertTrue(writeEx is RecorderException)
        assertTrue(connEx is RecorderException)
        assertTrue(parseEx is RecorderException)
    }
}
