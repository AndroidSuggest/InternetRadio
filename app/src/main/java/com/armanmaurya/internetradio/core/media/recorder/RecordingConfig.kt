package com.armanmaurya.internetradio.core.media.recorder

data class RecordingConfig(
    val url: String,
    val title: String,
    val bitrateKbps: Int? = null,
    val bufferSizeBytes: Int = 8192,
    val connectionTimeoutMs: Long = 15_000L,
    val userAgent: String? = null,
    val maxRetryDurationMs: Long = 300_000L,
    val minRetryDelayMs: Long = 1000L,
    val maxRetryDelayMs: Long = 15_000L,
    val maxRetryDurationProvider: (() -> Long)? = null
) {
    fun getMaxRetryDuration(): Long = maxRetryDurationProvider?.invoke() ?: maxRetryDurationMs
}
