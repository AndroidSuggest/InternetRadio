package com.armanmaurya.internetradio.core.media.recorder

sealed class RecorderException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class StreamConnectionException(url: String, cause: Throwable? = null) :
        RecorderException("Failed to connect to stream at: $url", cause)

    class UnsupportedFormatException(contentType: String?) :
        RecorderException("Unsupported stream content type: $contentType")

    class SinkWriteException(message: String, cause: Throwable? = null) :
        RecorderException("Storage sink write error: $message", cause)

    class PlaylistParseException(message: String, cause: Throwable? = null) :
        RecorderException("HLS playlist parsing failed: $message", cause)
}
