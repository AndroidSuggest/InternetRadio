package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.ScheduleDao
import com.armanmaurya.internetradio.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: ScheduleDao
) : ScheduleRepository {
    override fun getAllSchedules(): Flow<List<ScheduleEntity>> = scheduleDao.getAllSchedules()

    override suspend fun getScheduleById(id: Int): ScheduleEntity? = scheduleDao.getScheduleById(id)

    override suspend fun insertSchedule(schedule: ScheduleEntity): Long = scheduleDao.insertSchedule(schedule)

    override suspend fun updateSchedule(schedule: ScheduleEntity) = scheduleDao.updateSchedule(schedule)

    override suspend fun deleteSchedule(schedule: ScheduleEntity) = scheduleDao.deleteSchedule(schedule)

    override suspend fun updateScheduleStatus(id: Int, isEnabled: Boolean) = scheduleDao.updateScheduleStatus(id, isEnabled)
}
