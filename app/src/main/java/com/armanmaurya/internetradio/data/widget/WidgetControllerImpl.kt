package com.armanmaurya.internetradio.data.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.core.utils.extractPaletteFromBitmap
import com.armanmaurya.internetradio.core.utils.resolveArtwork
import com.armanmaurya.internetradio.domain.controller.WidgetController
import com.armanmaurya.internetradio.domain.model.WidgetPlaybackPayload
import com.armanmaurya.internetradio.ui.widget.NowPlayingWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WidgetController {

    @Volatile
    override var latestPayload: WidgetPlaybackPayload? = null
        private set

    override fun clearLatestPayload() {
        latestPayload = null
    }

    override suspend fun updatePlayback(
        title: String,
        artist: String,
        artworkUrl: String?,
        isPlaying: Boolean,
        hasNext: Boolean,
        hasPrev: Boolean,
        stationName: String?,
        stationThumbnailUrl: String?,
        isCoverArtFetched: Boolean
    ) {
        val resolvedBmp = resolveArtwork(context, artworkUrl)
        val paletteColors = extractPaletteFromBitmap(resolvedBmp)
            ?: if (!artworkUrl.isNullOrBlank() && artworkUrl != stationThumbnailUrl) {
                extractPaletteFromBitmap(resolveArtwork(context, stationThumbnailUrl, maxDimension = 96))
            } else null

        latestPayload = WidgetPlaybackPayload(
            title = title,
            artist = artist,
            artworkUrl = artworkUrl,
            isPlaying = isPlaying,
            hasNext = hasNext,
            hasPrev = hasPrev,
            stationName = stationName,
            stationThumbnailUrl = stationThumbnailUrl,
            isCoverArtFetched = isCoverArtFetched,
            bgColor = paletteColors?.backgroundColor,
            titleColor = paletteColors?.titleTextColor,
            artistColor = paletteColors?.artistTextColor,
            dayBgColor = paletteColors?.dayBackgroundColor,
            dayTitleColor = paletteColors?.dayTitleTextColor,
            dayArtistColor = paletteColors?.dayArtistTextColor,
            seedColor = paletteColors?.seedColor,
            bgAlpha = latestPayload?.bgAlpha,
        )

        try {
            val manager = GlanceAppWidgetManager(context)
            val widget = NowPlayingWidget()

            manager.getGlanceIds(NowPlayingWidget::class.java).forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[WidgetStateKeys.TITLE]                 = title
                    prefs[WidgetStateKeys.ARTIST]                = artist
                    prefs[WidgetStateKeys.STATION_NAME]          = stationName ?: ""
                    prefs[WidgetStateKeys.ARTWORK_URL]           = artworkUrl ?: ""
                    prefs[WidgetStateKeys.STATION_THUMBNAIL_URL]  = stationThumbnailUrl ?: ""
                    prefs[WidgetStateKeys.IS_COVER_ART_FETCHED]   = isCoverArtFetched
                    prefs[WidgetStateKeys.IS_PLAYING]            = isPlaying
                    prefs[WidgetStateKeys.HAS_NEXT]              = hasNext
                    prefs[WidgetStateKeys.HAS_PREV]              = hasPrev

                    if (paletteColors?.backgroundColor != null) {
                        prefs[WidgetStateKeys.BG_COLOR] = paletteColors.backgroundColor
                    } else {
                        prefs.remove(WidgetStateKeys.BG_COLOR)
                    }
                    if (paletteColors?.titleTextColor != null) {
                        prefs[WidgetStateKeys.TITLE_COLOR] = paletteColors.titleTextColor
                    } else {
                        prefs.remove(WidgetStateKeys.TITLE_COLOR)
                    }
                    if (paletteColors?.artistTextColor != null) {
                        prefs[WidgetStateKeys.ARTIST_COLOR] = paletteColors.artistTextColor
                    } else {
                        prefs.remove(WidgetStateKeys.ARTIST_COLOR)
                    }
                    if (paletteColors?.dayBackgroundColor != null) {
                        prefs[WidgetStateKeys.DAY_BG_COLOR] = paletteColors.dayBackgroundColor
                    } else {
                        prefs.remove(WidgetStateKeys.DAY_BG_COLOR)
                    }
                    if (paletteColors?.dayTitleTextColor != null) {
                        prefs[WidgetStateKeys.DAY_TITLE_COLOR] = paletteColors.dayTitleTextColor
                    } else {
                        prefs.remove(WidgetStateKeys.DAY_TITLE_COLOR)
                    }
                    if (paletteColors?.dayArtistTextColor != null) {
                        prefs[WidgetStateKeys.DAY_ARTIST_COLOR] = paletteColors.dayArtistTextColor
                    } else {
                        prefs.remove(WidgetStateKeys.DAY_ARTIST_COLOR)
                    }
                    if (paletteColors?.seedColor != null) {
                        prefs[WidgetStateKeys.SEED_COLOR] = paletteColors.seedColor
                    } else {
                        prefs.remove(WidgetStateKeys.SEED_COLOR)
                    }
                }
                widget.update(context, glanceId)
            }
        } catch (e: Exception) {
            Log.e("WidgetControllerImpl", "Failed to update widget", e)
        }
    }

    override suspend fun updateWidgetAlpha(alpha: Float) {
        latestPayload = latestPayload?.copy(bgAlpha = alpha)
        try {
            val manager = GlanceAppWidgetManager(context)
            val widget = NowPlayingWidget()
            manager.getGlanceIds(NowPlayingWidget::class.java).forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[WidgetStateKeys.BG_ALPHA] = alpha
                }
                widget.update(context, glanceId)
            }
        } catch (e: Exception) {
            Log.e("WidgetControllerImpl", "Failed to update widget alpha", e)
        }
    }

    override suspend fun cleanStaleWidgetState(
        stationName: String?,
        favicon: String?,
        appWidgetIds: IntArray?
    ) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val widget = NowPlayingWidget()
            val fallbackTitle = stationName ?: context.getString(R.string.widget_nothing_playing)
            latestPayload = null

            val stationBmp = resolveArtwork(context, favicon)
            val paletteColors = extractPaletteFromBitmap(stationBmp)

            val glanceIds = if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                val resolved = mutableListOf<GlanceId>()
                for (id in appWidgetIds) {
                    try {
                        resolved.add(manager.getGlanceIdBy(id))
                    } catch (_: Exception) {}
                }
                if (resolved.isNotEmpty()) resolved else manager.getGlanceIds(NowPlayingWidget::class.java).toList()
            } else {
                manager.getGlanceIds(NowPlayingWidget::class.java).toList()
            }

            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[WidgetStateKeys.TITLE]                 = fallbackTitle
                    prefs[WidgetStateKeys.ARTIST]                = ""
                    prefs[WidgetStateKeys.STATION_NAME]          = stationName ?: ""
                    prefs[WidgetStateKeys.ARTWORK_URL]           = favicon ?: ""
                    prefs[WidgetStateKeys.STATION_THUMBNAIL_URL]  = ""
                    prefs[WidgetStateKeys.IS_COVER_ART_FETCHED]   = false
                    prefs[WidgetStateKeys.IS_PLAYING]            = false
                    prefs[WidgetStateKeys.HAS_NEXT]              = false
                    prefs[WidgetStateKeys.HAS_PREV]              = false

                    if (paletteColors?.backgroundColor != null) {
                        prefs[WidgetStateKeys.BG_COLOR] = paletteColors.backgroundColor
                    } else {
                        prefs.remove(WidgetStateKeys.BG_COLOR)
                    }
                    if (paletteColors?.titleTextColor != null) {
                        prefs[WidgetStateKeys.TITLE_COLOR] = paletteColors.titleTextColor
                    } else {
                        prefs.remove(WidgetStateKeys.TITLE_COLOR)
                    }
                    if (paletteColors?.artistTextColor != null) {
                        prefs[WidgetStateKeys.ARTIST_COLOR] = paletteColors.artistTextColor
                    } else {
                        prefs.remove(WidgetStateKeys.ARTIST_COLOR)
                    }
                    if (paletteColors?.dayBackgroundColor != null) {
                        prefs[WidgetStateKeys.DAY_BG_COLOR] = paletteColors.dayBackgroundColor
                    } else {
                        prefs.remove(WidgetStateKeys.DAY_BG_COLOR)
                    }
                    if (paletteColors?.dayTitleTextColor != null) {
                        prefs[WidgetStateKeys.DAY_TITLE_COLOR] = paletteColors.dayTitleTextColor
                    } else {
                        prefs.remove(WidgetStateKeys.DAY_TITLE_COLOR)
                    }
                    if (paletteColors?.dayArtistTextColor != null) {
                        prefs[WidgetStateKeys.DAY_ARTIST_COLOR] = paletteColors.dayArtistTextColor
                    } else {
                        prefs.remove(WidgetStateKeys.DAY_ARTIST_COLOR)
                    }
                    if (paletteColors?.seedColor != null) {
                        prefs[WidgetStateKeys.SEED_COLOR] = paletteColors.seedColor
                    } else {
                        prefs.remove(WidgetStateKeys.SEED_COLOR)
                    }
                }
                widget.update(context, glanceId)
            }
        } catch (e: Exception) {
            Log.e("WidgetControllerImpl", "Failed to clean stale widget state", e)
        }
    }
}
