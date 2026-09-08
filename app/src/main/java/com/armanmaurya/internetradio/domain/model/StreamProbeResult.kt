package com.armanmaurya.internetradio.domain.model

data class StreamProbeResult(
    val codec: String,
    val bitrate: Int,
    val name: String?,
    val description: String?,
    val genre: String?,
    val homepage: String?
)
