package com.armanmaurya.internetradio.data.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.armanmaurya.internetradio.domain.usecase.schedule.RescheduleAllSchedulesUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var rescheduleAllSchedulesUseCase: RescheduleAllSchedulesUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "InternetRadio:BootReceiverWakeLock"
            )
            wakeLock.acquire(60_000L)

            val pendingResult = goAsync()
            scope.launch {
                try {
                    rescheduleAllSchedulesUseCase()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        if (wakeLock.isHeld) {
                            wakeLock.release()
                        }
                    } catch (_: Exception) {}
                    pendingResult.finish()
                }
            }
        }
    }
}
