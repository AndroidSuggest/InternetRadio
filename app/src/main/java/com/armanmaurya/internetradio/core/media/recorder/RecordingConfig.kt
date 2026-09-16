package com.armanmaurya.internetradio.core.media.recorder

data class RecordingConfig(
    val url: String,
    val title: String,
    val bufferSizeBytes: Int = 8192,
    val connectionTimeoutMs: Long = 15_000L,
    val userAgent: String? = null
)
