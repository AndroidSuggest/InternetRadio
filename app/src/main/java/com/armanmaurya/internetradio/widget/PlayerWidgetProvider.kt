package com.armanmaurya.internetradio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import androidx.media3.common.Player
import coil3.BitmapImage
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.armanmaurya.internetradio.MainActivity
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.player.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        
        updateWidgets(context, appWidgetManager, appWidgetIds, null, null, null, null)

        val intent = Intent(context, PlaybackService::class.java).apply {
            action = "com.armanmaurya.internetradio.ACTION_WIDGET_UPDATE"
        }
        
        try {
            context.startService(intent)
        } catch (e: Exception) {
            // App is in background and idle. No state to sync.
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = "com.armanmaurya.internetradio.ACTION_WIDGET_UPDATE"
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            // App is in background and idle.
        }
    }

    companion object {
        private val scope = CoroutineScope(Dispatchers.IO)
        
        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            player: Player?,
            trackTitle: String?,
            stationName: String?,
            artworkUri: String?
        ) {
            val isPlaying = player?.isPlaying == true
            val playlistSize = player?.mediaItemCount ?: 0
            
            val playPauseIntent = Intent(context, PlaybackService::class.java).apply { action = "com.armanmaurya.internetradio.ACTION_WIDGET_PLAY_PAUSE" }
            val prevIntent = Intent(context, PlaybackService::class.java).apply { action = "com.armanmaurya.internetradio.ACTION_WIDGET_PREVIOUS" }
            val nextIntent = Intent(context, PlaybackService::class.java).apply { action = "com.armanmaurya.internetradio.ACTION_WIDGET_NEXT" }
            
            val pendingPlayPause = PendingIntent.getService(context, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val pendingPrev = PendingIntent.getService(context, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val pendingNext = PendingIntent.getService(context, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val launchIntent = Intent(context, MainActivity::class.java)
            val pendingLaunch = PendingIntent.getActivity(context, 3, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            if (!artworkUri.isNullOrBlank()) {
                scope.launch {
                    try {
                        var targetUrl = artworkUri
                        if (targetUrl.startsWith("content://") && targetUrl.contains(".svgproxy/")) {
                            val base64 = android.net.Uri.parse(targetUrl).lastPathSegment
                            if (base64 != null) {
                                targetUrl = String(android.util.Base64.decode(base64, android.util.Base64.URL_SAFE))
                            }
                        }

                        val request = ImageRequest.Builder(context)
                            .data(targetUrl)
                            .size(256)
                            .build()
                            
                        val result = context.imageLoader.execute(request)
                        var swBitmap: Bitmap? = null
                        if (result is SuccessResult) {
                            val bitmap = (result.image as? BitmapImage)?.bitmap 
                                ?: (result.image.asDrawable(context.resources) as? BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                swBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                                    bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
                                } else {
                                    bitmap
                                }
                            }
                        }
                        
                        applyWidgetUpdates(context, appWidgetManager, appWidgetIds, isPlaying, playlistSize, trackTitle, stationName, pendingPlayPause, pendingPrev, pendingNext, pendingLaunch, swBitmap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        applyWidgetUpdates(context, appWidgetManager, appWidgetIds, isPlaying, playlistSize, trackTitle, stationName, pendingPlayPause, pendingPrev, pendingNext, pendingLaunch, null)
                    }
                }
            } else {
                applyWidgetUpdates(context, appWidgetManager, appWidgetIds, isPlaying, playlistSize, trackTitle, stationName, pendingPlayPause, pendingPrev, pendingNext, pendingLaunch, null)
            }
        }

        private fun applyWidgetUpdates(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            isPlaying: Boolean,
            playlistSize: Int,
            trackTitle: String?,
            stationName: String?,
            pendingPlayPause: PendingIntent,
            pendingPrev: PendingIntent,
            pendingNext: PendingIntent,
            pendingLaunch: PendingIntent,
            artworkBitmap: Bitmap?
        ) {
            for (appWidgetId in appWidgetIds) {
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 50)
                val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                
                // If the widget is shrunk to less than 100dp tall, use the small horizontal layout
                val layoutId = if (minHeight < 100) R.layout.widget_player_small else R.layout.widget_player
                
                val views = RemoteViews(context.packageName, layoutId)
                views.setOnClickPendingIntent(R.id.widget_root, pendingLaunch)
                views.setOnClickPendingIntent(R.id.widget_btn_play_pause, pendingPlayPause)
                views.setOnClickPendingIntent(R.id.widget_btn_prev, pendingPrev)
                views.setOnClickPendingIntent(R.id.widget_btn_next, pendingNext)
                
                // Hide prev/next buttons if widget is narrow or if playlist has only 1 item
                if (minWidth < 300 || playlistSize <= 1) {
                    views.setViewVisibility(R.id.widget_btn_prev, android.view.View.GONE)
                    views.setViewVisibility(R.id.widget_btn_next, android.view.View.GONE)
                } else {
                    views.setViewVisibility(R.id.widget_btn_prev, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.widget_btn_next, android.view.View.VISIBLE)
                }

                views.setTextViewText(R.id.widget_track_title, trackTitle ?: context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_station_name, stationName ?: "")

                views.setImageViewResource(
                    R.id.widget_btn_play_pause,
                    if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
                )
                
                if (artworkBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_album_art, getRoundedCornerBitmap(artworkBitmap, 16f))
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        val tint = getArtworkTint(artworkBitmap)
                        views.setColorStateList(R.id.widget_root, "setBackgroundTintList", android.content.res.ColorStateList.valueOf(tint))
                        
                        val white = android.graphics.Color.WHITE
                        val dark = android.graphics.Color.parseColor("#121212")
                        views.setTextColor(R.id.widget_track_title, white)
                        views.setTextColor(R.id.widget_station_name, android.graphics.Color.parseColor("#B3FFFFFF"))
                        views.setColorStateList(R.id.widget_btn_play_pause, "setImageTintList", android.content.res.ColorStateList.valueOf(dark))
                        views.setColorStateList(R.id.widget_btn_prev, "setImageTintList", android.content.res.ColorStateList.valueOf(white))
                        views.setColorStateList(R.id.widget_btn_next, "setImageTintList", android.content.res.ColorStateList.valueOf(white))
                    }
                } else {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.media3_notification_small_icon)
                }
                
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun getRoundedCornerBitmap(bitmap: Bitmap, pixels: Float): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(output)

            val color = -0xbdbdbe
            val paint = android.graphics.Paint()
            val rect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
            val rectF = android.graphics.RectF(rect)

            paint.isAntiAlias = true
            canvas.drawARGB(0, 0, 0, 0)
            paint.color = color
            
            // Draw full rounded rect
            canvas.drawRoundRect(rectF, pixels, pixels, paint)

            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)

            return output
        }

        private fun getArtworkTint(bitmap: Bitmap): Int {
            val scaled = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
            val avgColor = scaled.getPixel(0, 0)
            scaled.recycle()
            
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(avgColor, hsv)
            
            hsv[2] = hsv[2].coerceAtMost(0.3f)
            hsv[1] = (hsv[1] * 1.5f).coerceAtMost(1f)
            
            return android.graphics.Color.HSVToColor(hsv)
        }
    }
}
