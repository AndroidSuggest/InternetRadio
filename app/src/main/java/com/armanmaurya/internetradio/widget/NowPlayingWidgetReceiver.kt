package com.armanmaurya.internetradio.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.armanmaurya.internetradio.player.PlaybackService

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

/**
 * Manifest-registered BroadcastReceiver for widget playback controls.
 * Using actionSendBroadcast to this receiver is guaranteed to be delivered
 * on ALL Android devices including aggressive OEM launchers like Realme/MIUI.
 * From BroadcastReceiver.onReceive(), the OS grants a temporary allowance to
 * start foreground services regardless of battery optimization restrictions.
 */
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
