package com.armanmaurya.internetradio.data.schedule

import android.content.Context
import android.os.PowerManager

/**
 * Bridges CPU wakefulness between ScheduleReceiver receiving the alarm broadcast
 * and AlarmService connecting and launching the stream playback.
 * Includes a safety timeout (default 90 seconds) to prevent battery drain.
 */
object AlarmWakeLockBridge {
    private var wakeLock: PowerManager.WakeLock? = null
    private val lock = Any()

    fun acquire(context: Context, timeoutMs: Long = 90_000L) {
        synchronized(lock) {
            if (wakeLock == null) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "InternetRadio:AlarmWakeLockBridge"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(timeoutMs)
        }
    }

    fun release() {
        synchronized(lock) {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            } catch (_: Exception) {}
        }
    }
}
