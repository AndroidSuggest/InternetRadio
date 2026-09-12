package com.armanmaurya.internetradio.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import com.armanmaurya.internetradio.player.ScheduleReceiver
import com.armanmaurya.internetradio.ui.mobile.MobileActivity
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {
    @Inject lateinit var recordingManager: RecordingManager
    @Inject lateinit var scheduleRepository: ScheduleRepository
    @Inject lateinit var libraryRepository: LibraryRepository
    private var notificationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val stationJson = intent.getStringExtra("STATION_JSON")
                val station = if (stationJson != null) {
                    Gson().fromJson(stationJson, RadioStation::class.java)
                } else null
                station?.let { recordingManager.startRecording(it) }
                startForeground(NOTIF_ID, buildGroupSummaryNotification(recordingManager.sessionsFlow.value))
                observeSessions()
            }
            ACTION_START_FROM_SCHEDULE -> {
                val scheduleId = intent.getIntExtra(ScheduleReceiver.EXTRA_SCHEDULE_ID, -1)
                
                if (scheduleId != -1) {
                    startForeground(NOTIF_ID, buildGroupSummaryNotification(recordingManager.sessionsFlow.value))
                    scope.launch(Dispatchers.IO) {
                        val schedule = scheduleRepository.getScheduleById(scheduleId) ?: return@launch
                        val station = libraryRepository.getStationById(schedule.stationUuid) ?: return@launch
                        recordingManager.startRecording(station)
                        observeSessions()
                        
                        if (schedule.durationMinutes > 0) {
                            val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
                            val stopIntent = Intent(this@RecordingService, ScheduleReceiver::class.java).apply {
                                this.action = ScheduleReceiver.ACTION_STOP_RECORDING
                                putExtra("KEEP_PLAYBACK", schedule.keepPlayback)
                                putExtra("UUID", station.stationUuid)
                            }
                            val pendingIntent = PendingIntent.getBroadcast(
                                this@RecordingService,
                                station.stationUuid.hashCode(),
                                stopIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            val stopAt = System.currentTimeMillis() + (schedule.durationMinutes * 60 * 1000L)
                            alarmManager.setAlarmClock(android.app.AlarmManager.AlarmClockInfo(stopAt, pendingIntent), pendingIntent)
                        }
                    }
                }
            }
            ACTION_STOP -> {
                val uuid = intent.getStringExtra("UUID") ?: return START_STICKY
                recordingManager.stopRecording(uuid)
                if (recordingManager.sessionsFlow.value.isEmpty()) stopSelf()
            }
        }
        return START_STICKY
    }

    private val activeNotificationIds = mutableSetOf<Int>()
    private var updateTickerJob: Job? = null

    private fun observeSessions() {
        if (notificationJob != null) return
        notificationJob = recordingManager.sessionsFlow.onEach { sessions ->
            val notificationManager = getSystemService(NotificationManager::class.java)
            if (sessions.isEmpty()) {
                updateTickerJob?.cancel()
                activeNotificationIds.forEach { notificationManager.cancel(it) }
                activeNotificationIds.clear()
                stopSelf()
            } else {
                val currentIds = sessions.keys.map { it.hashCode() }.toSet()
                
                val stoppedIds = activeNotificationIds - currentIds
                stoppedIds.forEach { notificationManager.cancel(it) }
                
                activeNotificationIds.clear()
                activeNotificationIds.addAll(currentIds)
                
                notificationManager.notify(NOTIF_ID, buildGroupSummaryNotification(sessions))
                
                sessions.forEach { (uuid, session) ->
                    notificationManager.notify(uuid.hashCode(), buildChildNotification(session))
                }
                
                if (updateTickerJob == null || updateTickerJob?.isActive != true) {
                    updateTickerJob = scope.launch {
                        while (isActive) {
                            delay(3000)
                            val currentSessions = recordingManager.sessionsFlow.value
                            currentSessions.forEach { (uuid, session) ->
                                notificationManager.notify(uuid.hashCode(), buildChildNotification(session))
                            }
                        }
                    }
                }
            }
        }.launchIn(scope)
    }

    private fun buildGroupSummaryNotification(sessions: Map<String, RecordingState>): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "recording_channel",
                "Active Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val contentIntent = Intent(this, MobileActivity::class.java).apply {
            putExtra("open_tab", "recordings")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "recording_channel")
            .setSmallIcon(R.drawable.media3_notification_small_icon)
            .setOngoing(true)
            .setGroup("GROUP_RECORDINGS")
            .setGroupSummary(true)
            .setContentTitle(if (sessions.isEmpty()) "Preparing recording..." else "Recording ${sessions.size} station(s)")
            .setContentIntent(contentPendingIntent)
            .build()
    }

    private fun buildChildNotification(session: RecordingState): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
            putExtra("UUID", session.station.stationUuid)
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            session.station.stationUuid.hashCode(),
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentIntent = Intent(this, MobileActivity::class.java).apply {
            putExtra("open_tab", "recordings")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sizeMb = session.bytesWritten / (1024f * 1024f)
        val sizeText = String.format(java.util.Locale.US, "%.2f MB", sizeMb)

        return NotificationCompat.Builder(this, "recording_channel")
            .setSmallIcon(R.drawable.media3_notification_small_icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup("GROUP_RECORDINGS")
            .setContentTitle("Recording: ${session.station.name}")
            .setContentText(sizeText)
            .setContentIntent(contentPendingIntent)
            .setUsesChronometer(true)
            .setWhen(session.startTimeMs)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        updateTickerJob?.cancel()
        notificationJob?.cancel()
        recordingManager.stopAllRecordings()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.armanmaurya.internetradio.REC_START"
        const val ACTION_START_FROM_SCHEDULE = "com.armanmaurya.internetradio.REC_START_SCHEDULE"
        const val ACTION_STOP  = "com.armanmaurya.internetradio.REC_STOP"
        const val NOTIF_ID     = 3001
    }
}
