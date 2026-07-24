package com.armanmaurya.internetradio.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.armanmaurya.internetradio.data.repository.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var scheduleManager: ScheduleManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            scope.launch {
                val schedules = scheduleRepository.getAllSchedules().first()
                schedules.filter { it.isEnabled }.forEach { schedule ->
                    scheduleManager.schedule(schedule)
                }
            }
        }
    }
}
