package com.armanmaurya.internetradio.domain.model

data class WidgetPlaybackPayload(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val hasNext: Boolean,
    val hasPrev: Boolean,
    val stationName: String? = null,
    val stationThumbnailUrl: String? = null,
    val isCoverArtFetched: Boolean = false,
    val bgColor: Int? = null,
    val titleColor: Int? = null,
    val artistColor: Int? = null,
    val dayBgColor: Int? = null,
    val dayTitleColor: Int? = null,
    val dayArtistColor: Int? = null,
    val seedColor: Int? = null,
    val bgAlpha: Float? = null,
)
