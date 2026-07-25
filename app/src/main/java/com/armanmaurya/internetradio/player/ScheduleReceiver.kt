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
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

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
            recordingManager.stopRecording()
            val keepPlayback = intent.getBooleanExtra("KEEP_PLAYBACK", false)
            if (!keepPlayback) {
                playerController.stop()
            }
            return
        }

        val scheduleId = intent.getIntExtra(EXTRA_SCHEDULE_ID, -1)
        if (scheduleId == -1) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val schedule = scheduleRepository.getScheduleById(scheduleId) ?: return@launch
    
                if (!schedule.isEnabled) return@launch

                // Resolve the station from the library so PlaybackService can
                // build the MediaItem synchronously without any DB lookup.
                val libraryStation = libraryRepository.getStationById(schedule.stationUuid)
    
                val playIntent = Intent(context, PlaybackService::class.java).apply {
                    this.action = "com.armanmaurya.internetradio.ACTION_PLAY_STATION"
                    putExtra("STATION_UUID", schedule.stationUuid)
                    putExtra("STATION_NAME", schedule.stationName)
                    putExtra("STATION_URL", libraryStation?.urlResolved ?: libraryStation?.url ?: "")
                    putExtra("STATION_FAVICON", libraryStation?.favicon ?: "")
                    putExtra("START_RECORDING", schedule.type == ScheduleType.RECORD)
                    putExtra("RECORDING_DURATION", schedule.durationMinutes)
                    putExtra("KEEP_PLAYBACK", schedule.keepPlayback)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(playIntent)
                } else {
                    context.startService(playIntent)
                }
    
                // Reschedule if recurring, else disable
                if (schedule.isRecurring) {
                    scheduleManager.schedule(schedule)
                } else {
                    scheduleRepository.updateScheduleStatus(schedule.id, false)
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
