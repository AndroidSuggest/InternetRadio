package com.armanmaurya.internetradio.widget.state

import androidx.glance.ImageProvider
import androidx.glance.unit.ColorProvider

data class NowPlayingWidgetState(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val artwork: ImageProvider?,
    val isPlaying: Boolean,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
    val backgroundColor: ColorProvider? = null,
    val titleColor: ColorProvider? = null,
    val artistColor: ColorProvider? = null,
) {
    companion object {
        val Empty = NowPlayingWidgetState(
            title      = "Nothing playing",
            artist     = "",
            artworkUrl = null,
            artwork    = null,
            isPlaying  = false,
            hasNext    = false,
            hasPrev    = false,
            backgroundColor = null,
            titleColor = null,
            artistColor = null,
        )
    }
}
