package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun getAllSchedules(): Flow<List<ScheduleEntity>>
    suspend fun getScheduleById(id: Int): ScheduleEntity?
    suspend fun insertSchedule(schedule: ScheduleEntity): Long
    suspend fun updateSchedule(schedule: ScheduleEntity)
    suspend fun deleteSchedule(schedule: ScheduleEntity)
    suspend fun updateScheduleStatus(id: Int, isEnabled: Boolean)
}
