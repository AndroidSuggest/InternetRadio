package com.armanmaurya.internetradio.domain.usecase.schedule

import com.armanmaurya.internetradio.domain.controller.ScheduleController
import com.armanmaurya.internetradio.domain.model.Schedule
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import javax.inject.Inject

class ToggleScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleController: ScheduleController
) {
    suspend operator fun invoke(schedule: Schedule, isEnabled: Boolean) {
        scheduleRepository.updateScheduleStatus(schedule.id, isEnabled)
        val updated = schedule.copy(isEnabled = isEnabled)
        if (isEnabled) {
            scheduleController.schedule(updated)
        } else {
            scheduleController.cancel(schedule.id)
        }
    }
}
