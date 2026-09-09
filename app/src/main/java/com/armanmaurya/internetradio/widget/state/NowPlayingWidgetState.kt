package com.armanmaurya.internetradio.widget.state

import androidx.glance.ImageProvider
import androidx.glance.unit.ColorProvider

data class NowPlayingWidgetState(
    val title: String,
    val artist: String,
    val stationName: String? = null,
    val artworkUrl: String?,
    val artwork: ImageProvider?,
    val stationThumbnailUrl: String? = null,
    val stationThumbnail: ImageProvider? = null,
    val isCoverArtFetched: Boolean = false,
    val isPlaying: Boolean,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
    val backgroundColor: ColorProvider? = null,
    val titleColor: ColorProvider? = null,
    val artistColor: ColorProvider? = null,
) {
    companion object {
        val Empty = NowPlayingWidgetState(
            title               = "",
            artist              = "",
            stationName         = null,
            artworkUrl          = null,
            artwork             = null,
            stationThumbnailUrl = null,
            stationThumbnail    = null,
            isCoverArtFetched   = false,
            isPlaying           = false,
            hasNext             = false,
            hasPrev             = false,
            backgroundColor     = null,
            titleColor          = null,
            artistColor         = null,
        )
    }
}
