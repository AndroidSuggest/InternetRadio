package com.armanmaurya.internetradio.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.app.AlarmManager
import android.os.Build
import com.armanmaurya.internetradio.data.local.entity.ScheduleType
import com.armanmaurya.internetradio.data.repository.LibraryRepository
import com.armanmaurya.internetradio.data.repository.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var settingsRepository: com.armanmaurya.internetradio.data.repository.SettingsRepository

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var scheduleManager: ScheduleManager

    @Inject
    lateinit var recordingManager: RecordingManager

    @Inject
    lateinit var playerController: PlayerController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action == ACTION_STOP_RECORDING) {
            val uuid = intent.getStringExtra("UUID")
            if (uuid != null) {
                val stopIntent = Intent(context, BackgroundRecordingService::class.java).apply {
                    this.action = BackgroundRecordingService.ACTION_STOP
                    putExtra("UUID", uuid)
                }
                context.startService(stopIntent)
            }
            val keepPlayback = intent.getBooleanExtra("KEEP_PLAYBACK", false)
            if (!keepPlayback) {
                playerController.stop()
            }
            return
        }

        val scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, -1)
        if (scheduleId == -1) return

        val type = intent.getStringExtra("EXTRA_TYPE")
        val playOnRecording = intent.getBooleanExtra("EXTRA_PLAY_ON_RECORDING", true)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "InternetRadio:ScheduleWakeLock")
        wakeLock.acquire(60_000L)

        val isPlayback = type == ScheduleType.PLAYBACK.name || (type == ScheduleType.RECORD.name && playOnRecording)
        val isRecord = type == ScheduleType.RECORD.name

        if (isRecord) {
            val recordIntent = Intent(context, BackgroundRecordingService::class.java).apply {
                this.action = BackgroundRecordingService.ACTION_START_FROM_SCHEDULE
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(recordIntent)
            } else {
                context.startService(recordIntent)
            }
        }

        if (isPlayback) {
            val playIntent = Intent(context, PlaybackService::class.java).apply {
                this.action = "com.armanmaurya.internetradio.ACTION_PLAY_SCHEDULE"
                putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(playIntent)
            } else {
                context.startService(playIntent)
            }
        }

        // Reschedule in background using goAsync
        val pendingResult = goAsync()
        scope.launch {
            try {
                val schedule = scheduleRepository.getScheduleById(scheduleId)
                if (schedule != null) {
                    if (schedule.isRecurring) {
                        scheduleManager.schedule(schedule)
                    } else {
                        scheduleRepository.updateScheduleStatus(schedule.id, false)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val ACTION_STOP_RECORDING = "com.armanmaurya.internetradio.ACTION_STOP_RECORDING"
    }
}
