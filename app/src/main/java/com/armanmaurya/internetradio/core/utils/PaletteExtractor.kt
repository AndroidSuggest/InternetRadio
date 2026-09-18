package com.armanmaurya.internetradio.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.ktx.themeColor

const val MAX_WIDGET_ARTWORK_SIZE = 256

suspend fun resolveArtwork(
    context: Context,
    url: String?,
    maxDimension: Int = MAX_WIDGET_ARTWORK_SIZE
): Bitmap? {
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
        Log.e("PaletteExtractor", "Failed to load artwork", e)
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
