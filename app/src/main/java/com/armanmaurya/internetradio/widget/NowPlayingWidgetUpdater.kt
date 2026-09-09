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

suspend fun resolveArtwork(context: Context, url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null

    return try {
        val loader = context.imageLoader
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(MAX_WIDGET_ARTWORK_SIZE)
            .build()

        val result = loader.execute(request)
        val rawBitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return null
        
        // RemoteViews cannot hold hardware-backed bitmaps
        val swBitmap = if (rawBitmap.config == Bitmap.Config.HARDWARE) {
            rawBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: rawBitmap
        } else {
            rawBitmap
        }

        // Guarantee bitmap dimensions do not exceed MAX_WIDGET_ARTWORK_SIZE
        // to prevent TransactionTooLargeException in RemoteViews / Glance IPC
        if (swBitmap.width > MAX_WIDGET_ARTWORK_SIZE || swBitmap.height > MAX_WIDGET_ARTWORK_SIZE) {
            val scale = minOf(
                MAX_WIDGET_ARTWORK_SIZE.toFloat() / swBitmap.width,
                MAX_WIDGET_ARTWORK_SIZE.toFloat() / swBitmap.height
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
) {
    try {
        val manager = GlanceAppWidgetManager(context)
        val widget = NowPlayingWidget()
        
        manager.getGlanceIds(NowPlayingWidget::class.java).forEach { glanceId ->
            // Use the Preferences-specific overload of updateAppWidgetState
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[WidgetStateKeys.TITLE]       = title
                prefs[WidgetStateKeys.ARTIST]      = artist
                prefs[WidgetStateKeys.ARTWORK_URL] = artworkUrl ?: ""
                prefs[WidgetStateKeys.IS_PLAYING]  = isPlaying
                prefs[WidgetStateKeys.HAS_NEXT]    = hasNext
                prefs[WidgetStateKeys.HAS_PREV]    = hasPrev
            }
            
            widget.update(context, glanceId)
        }
    } catch (e: Exception) {
        Log.e("NowPlayingWidget", "Failed to update widget", e)
    }
}
