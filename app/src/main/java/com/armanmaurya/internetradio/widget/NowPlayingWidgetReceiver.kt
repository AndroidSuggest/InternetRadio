package com.armanmaurya.internetradio.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.armanmaurya.internetradio.player.PlaybackService

import android.appwidget.AppWidgetManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        if (PlaybackService.isRunning) {
            PlaybackService.requestWidgetUpdate()
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        WidgetEntryPoint::class.java
                    )
                    val lastStation = entryPoint.recentRepository().getAllRecent().first().firstOrNull()
                    cleanStaleWidgetState(context.applicationContext, lastStation?.name, lastStation?.favicon)
                } catch (e: Exception) {
                    cleanStaleWidgetState(context.applicationContext, null, null)
                }
            }
        }
    }
}

class WidgetControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        context.startForegroundService(
            Intent(context, PlaybackService::class.java).apply {
                this.action = action
            }
        )
    }
}
