package com.armanmaurya.internetradio.domain.usecase.schedule

import com.armanmaurya.internetradio.domain.controller.ScheduleController
import com.armanmaurya.internetradio.domain.model.Schedule
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import javax.inject.Inject

class SaveScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleController: ScheduleController
) {
    suspend operator fun invoke(schedule: Schedule): Long {
        return if (schedule.id == 0) {
            val generatedId = scheduleRepository.insertSchedule(schedule)
            val savedSchedule = schedule.copy(id = generatedId.toInt())
            if (savedSchedule.isEnabled) {
                scheduleController.schedule(savedSchedule)
            }
            generatedId
        } else {
            scheduleRepository.updateSchedule(schedule)
            if (schedule.isEnabled) {
                scheduleController.schedule(schedule)
            } else {
                scheduleController.cancel(schedule.id)
            }
            schedule.id.toLong()
        }
    }
}
