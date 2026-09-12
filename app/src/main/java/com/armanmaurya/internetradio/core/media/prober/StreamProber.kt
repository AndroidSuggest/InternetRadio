package com.armanmaurya.internetradio.core.media.prober

data class StreamProbeResult(
    val codec: String,
    val bitrate: Int,
    val name: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val homepage: String? = null
)

interface StreamProber {
    suspend fun probe(url: String): StreamProbeResult?
}
