package com.armanmaurya.internetradio.domain.usecase.schedule

import com.armanmaurya.internetradio.domain.controller.ScheduleController
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RescheduleAllSchedulesUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleController: ScheduleController
) {
    suspend operator fun invoke() {
        val schedules = scheduleRepository.getAllSchedules().first()
        schedules.filter { it.isEnabled }.forEach { schedule ->
            scheduleController.schedule(schedule)
        }
    }
}
