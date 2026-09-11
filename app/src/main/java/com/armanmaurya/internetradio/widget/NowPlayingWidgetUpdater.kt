package com.armanmaurya.internetradio.widget

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.updateAll
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_WIDGET_ARTWORK_SIZE = 256

suspend fun resolveArtwork(context: Context, url: String?, maxDimension: Int = MAX_WIDGET_ARTWORK_SIZE): Bitmap? {
    if (url.isNullOrBlank()) return null

    return try {
        val loader = context.imageLoader
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(maxDimension)
            .build()

        val result = loader.execute(request)
        val rawBitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return null
        
        // RemoteViews cannot hold hardware-backed bitmaps
        val swBitmap = if (rawBitmap.config == Bitmap.Config.HARDWARE) {
            rawBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: rawBitmap
        } else {
            rawBitmap
        }

        // Guarantee bitmap dimensions do not exceed maxDimension
        // to prevent TransactionTooLargeException in RemoteViews / Glance IPC
        if (swBitmap.width > maxDimension || swBitmap.height > maxDimension) {
            val scale = minOf(
                maxDimension.toFloat() / swBitmap.width,
                maxDimension.toFloat() / swBitmap.height
            )
            val targetWidth = (swBitmap.width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (swBitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(swBitmap, targetWidth, targetHeight, true)
        } else {
            swBitmap
        }
    } catch (e: Exception) {
        Log.e("NowPlayingWidget", "Failed to load artwork", e)
        null
    }
}

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
)

@Volatile
var latestWidgetPayload: WidgetPlaybackPayload? = null
    internal set

/**
 * Saves the given playback state to the widget DataStore and then triggers
 * a re-render of every instance of [NowPlayingWidget] on the home screen.
 */
suspend fun pushWidgetUpdate(
    context: Context,
    title: String,
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    hasNext: Boolean,
    hasPrev: Boolean,
    stationName: String? = null,
    stationThumbnailUrl: String? = null,
    isCoverArtFetched: Boolean = false,
) {
    latestWidgetPayload = WidgetPlaybackPayload(
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        isPlaying = isPlaying,
        hasNext = hasNext,
        hasPrev = hasPrev,
        stationName = stationName,
        stationThumbnailUrl = stationThumbnailUrl,
        isCoverArtFetched = isCoverArtFetched
    )

    try {
        val manager = GlanceAppWidgetManager(context)
        val widget = NowPlayingWidget()
        
        manager.getGlanceIds(NowPlayingWidget::class.java).forEach { glanceId ->
            // Use the Preferences-specific overload of updateAppWidgetState
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
            }
            
            widget.update(context, glanceId)
        }
    } catch (e: Exception) {
        Log.e("NowPlayingWidget", "Failed to update widget", e)
    }
}

/**
 * Resets on-disk widget preferences to an idle station state when the service is not running.
 */
suspend fun cleanStaleWidgetState(
    context: Context,
    stationName: String?,
    favicon: String?,
    targetGlanceId: GlanceId? = null
) {
    try {
        val manager = GlanceAppWidgetManager(context)
        val widget = NowPlayingWidget()
        val fallbackTitle = stationName ?: context.getString(com.armanmaurya.internetradio.R.string.widget_nothing_playing)
        latestWidgetPayload = null
        val glanceIds = if (targetGlanceId != null) listOf(targetGlanceId) else manager.getGlanceIds(NowPlayingWidget::class.java)
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
            }
            widget.update(context, glanceId)
        }
    } catch (e: Exception) {
        Log.e("NowPlayingWidget", "Failed to clean stale widget state", e)
    }
}
