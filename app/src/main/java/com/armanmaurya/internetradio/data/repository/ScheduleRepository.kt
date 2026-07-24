package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.ScheduleDao
import com.armanmaurya.internetradio.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val scheduleDao: ScheduleDao
) {
    fun getAllSchedules(): Flow<List<ScheduleEntity>> = scheduleDao.getAllSchedules()

    suspend fun getScheduleById(id: Int): ScheduleEntity? = scheduleDao.getScheduleById(id)

    suspend fun insertSchedule(schedule: ScheduleEntity): Long = scheduleDao.insertSchedule(schedule)

    suspend fun updateSchedule(schedule: ScheduleEntity) = scheduleDao.updateSchedule(schedule)

    suspend fun deleteSchedule(schedule: ScheduleEntity) = scheduleDao.deleteSchedule(schedule)

    suspend fun updateScheduleStatus(id: Int, isEnabled: Boolean) = scheduleDao.updateScheduleStatus(id, isEnabled)
}
