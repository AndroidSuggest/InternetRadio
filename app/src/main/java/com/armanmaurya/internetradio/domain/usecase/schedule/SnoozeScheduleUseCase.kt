package com.armanmaurya.internetradio.domain.usecase.schedule

import com.armanmaurya.internetradio.domain.controller.ScheduleController
import javax.inject.Inject

class SnoozeScheduleUseCase @Inject constructor(
    private val scheduleController: ScheduleController
) {
    operator fun invoke(scheduleId: Int, minutes: Int = 10) {
        scheduleController.snooze(scheduleId, minutes)
    }
}
