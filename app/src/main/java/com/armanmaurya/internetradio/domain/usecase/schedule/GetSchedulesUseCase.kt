package com.armanmaurya.internetradio.domain.usecase.schedule

import com.armanmaurya.internetradio.domain.model.Schedule
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSchedulesUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {
    operator fun invoke(): Flow<List<Schedule>> = scheduleRepository.getAllSchedules()
}
