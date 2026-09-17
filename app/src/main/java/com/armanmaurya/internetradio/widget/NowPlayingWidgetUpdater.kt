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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.ktx.themeColor
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

data class ExtractedPaletteColors(
    val seedColor: Int,
    val backgroundColor: Int,
    val titleTextColor: Int,
    val artistTextColor: Int,
    val dayBackgroundColor: Int,
    val dayTitleTextColor: Int,
    val dayArtistTextColor: Int,
)

fun extractPaletteFromBitmap(bitmap: Bitmap?): ExtractedPaletteColors? {
    if (bitmap == null) return null
    return try {
        val imageBitmap = bitmap.asImageBitmap()
        val seedColor = imageBitmap.themeColor(fallback = Color.Transparent)
        if (seedColor == Color.Transparent || seedColor == Color.Unspecified) {
            return null
        }
        val darkScheme = dynamicColorScheme(
            seedColor = seedColor,
            isDark = true,
            style = PaletteStyle.Vibrant
        )
        val lightScheme = dynamicColorScheme(
            seedColor = seedColor,
            isDark = false,
            style = PaletteStyle.Vibrant
        )
        ExtractedPaletteColors(
            seedColor = seedColor.toArgb(),
            backgroundColor = darkScheme.surfaceContainer.toArgb(),
            titleTextColor = darkScheme.onSurface.toArgb(),
            artistTextColor = darkScheme.onSurfaceVariant.toArgb(),
            dayBackgroundColor = lightScheme.surfaceContainer.toArgb(),
            dayTitleTextColor = lightScheme.onSurface.toArgb(),
            dayArtistTextColor = lightScheme.onSurfaceVariant.toArgb(),
        )
    } catch (e: Exception) {
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
    val bgColor: Int? = null,
    val titleColor: Int? = null,
    val artistColor: Int? = null,
    val dayBgColor: Int? = null,
    val dayTitleColor: Int? = null,
    val dayArtistColor: Int? = null,
    val seedColor: Int? = null,
    val bgAlpha: Float? = null,
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
    // Pre-resolve artwork and extract palette in background before updating widget (Spotify pattern)
    val resolvedBmp = resolveArtwork(context, artworkUrl)
    val paletteColors = extractPaletteFromBitmap(resolvedBmp)
        ?: if (!artworkUrl.isNullOrBlank() && artworkUrl != stationThumbnailUrl) {
            extractPaletteFromBitmap(resolveArtwork(context, stationThumbnailUrl, maxDimension = 96))
        } else null

    latestWidgetPayload = WidgetPlaybackPayload(
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
        bgAlpha = latestWidgetPayload?.bgAlpha,
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
        Log.e("NowPlayingWidget", "Failed to update widget", e)
    }
}

/**
 * Updates the background opacity/alpha for all active instances of [NowPlayingWidget].
 */
suspend fun updateWidgetAlpha(context: Context, alpha: Float) {
    latestWidgetPayload = latestWidgetPayload?.copy(bgAlpha = alpha)
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
        Log.e("NowPlayingWidget", "Failed to update widget alpha", e)
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
        val stationBmp = resolveArtwork(context, favicon)
        val paletteColors = extractPaletteFromBitmap(stationBmp)
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
        Log.e("NowPlayingWidget", "Failed to clean stale widget state", e)
    }
}
