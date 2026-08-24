package com.armanmaurya.internetradio.player

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.armanmaurya.internetradio.data.local.entity.ScheduleEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(entity: ScheduleEntity) {
        if (!entity.isEnabled) {
            cancel(entity.id)
            return
        }

        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            putExtra(ScheduleReceiver.EXTRA_SCHEDULE_ID, entity.id)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entity.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = getNextTriggerTime(entity)

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        } catch (e: SecurityException) {
            // Permission SCHEDULE_EXACT_ALARM is required on Android 12+
            // We should request it in UI before allowing to schedule.
            e.printStackTrace()
        }
    }

    fun cancel(scheduleId: Int) {
        val intent = Intent(context, ScheduleReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun getNextTriggerTime(entity: ScheduleEntity): Long {
        if (!entity.isRecurring && entity.triggerTimeInMillis > System.currentTimeMillis()) {
            return entity.triggerTimeInMillis
        }

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        calendar.set(Calendar.HOUR_OF_DAY, entity.timeHour)
        calendar.set(Calendar.MINUTE, entity.timeMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // If time has already passed today, check if it's recurring or one-time
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (entity.isRecurring && entity.daysOfWeek.isNotBlank()) {
            val days = entity.daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }
            if (days.isNotEmpty()) {
                // Find next valid day
                var currentDay = calendar.get(Calendar.DAY_OF_WEEK)
                var daysToAdd = 0
                while (!days.contains(currentDay)) {
                    currentDay++
                    if (currentDay > 7) currentDay = 1
                    daysToAdd++
                }
                if (daysToAdd > 0) {
                    calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
                }
            }
        }

        return calendar.timeInMillis
    }
}
