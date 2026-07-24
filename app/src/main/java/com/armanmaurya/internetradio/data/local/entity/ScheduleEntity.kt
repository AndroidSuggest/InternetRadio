package com.armanmaurya.internetradio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ScheduleType { PLAYBACK, RECORD }

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val stationUuid: String,
    val stationName: String,
    val type: ScheduleType,
    val triggerTimeInMillis: Long,
    val durationMinutes: Int = 0,
    val isRecurring: Boolean = false,
    val daysOfWeek: String = "", // E.g. "1,2,3,4,5"
    val timeHour: Int = 0,
    val timeMinute: Int = 0,
    val isEnabled: Boolean = true,
    @androidx.room.ColumnInfo(defaultValue = "1.0")
    val volumeLevel: Float = 1.0f,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val keepPlayback: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "''")
    val scheduleName: String = ""
)
