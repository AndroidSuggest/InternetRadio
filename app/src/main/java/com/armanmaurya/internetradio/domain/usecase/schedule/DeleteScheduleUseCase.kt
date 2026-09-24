package com.armanmaurya.internetradio.domain.usecase.schedule

import com.armanmaurya.internetradio.domain.controller.ScheduleController
import com.armanmaurya.internetradio.domain.model.Schedule
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import javax.inject.Inject

class DeleteScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleController: ScheduleController
) {
    suspend operator fun invoke(schedule: Schedule) {
        scheduleController.cancel(schedule.id)
        scheduleRepository.deleteSchedule(schedule)
    }
}
