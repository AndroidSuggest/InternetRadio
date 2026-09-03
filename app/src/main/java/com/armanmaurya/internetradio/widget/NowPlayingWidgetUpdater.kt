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

suspend fun resolveArtwork(context: Context, url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null

    return try {
        val loader = context.imageLoader
        val request = ImageRequest.Builder(context)
            .data(url)
            .build()

        // Removed the strict timeout so high-res or slow-network artwork doesn't fail
        val result = loader.execute(request)
        // toBitmap() may return a hardware-backed bitmap; RemoteViews can't hold those
        val rawBitmap = (result as? SuccessResult)?.image?.toBitmap()
        rawBitmap?.let { bmp ->
            if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false)
            else bmp
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
