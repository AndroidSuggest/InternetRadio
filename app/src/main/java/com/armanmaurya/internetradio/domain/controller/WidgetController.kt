package com.armanmaurya.internetradio.domain.controller

import com.armanmaurya.internetradio.domain.model.WidgetPlaybackPayload

interface WidgetController {
    val latestPayload: WidgetPlaybackPayload?

    suspend fun updatePlayback(
        title: String,
        artist: String,
        artworkUrl: String?,
        isPlaying: Boolean,
        hasNext: Boolean,
        hasPrev: Boolean,
        stationName: String? = null,
        stationThumbnailUrl: String? = null,
        isCoverArtFetched: Boolean = false,
    )

    suspend fun updateWidgetAlpha(alpha: Float)

    suspend fun cleanStaleWidgetState(
        stationName: String? = null,
        favicon: String? = null,
        appWidgetIds: IntArray? = null,
    )

    fun clearLatestPayload()
}
