package com.armanmaurya.internetradio.domain.usecase.schedule

import com.armanmaurya.internetradio.domain.model.Schedule
import com.armanmaurya.internetradio.domain.repository.ScheduleRepository
import javax.inject.Inject

class GetScheduleByIdUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {
    suspend operator fun invoke(id: Int): Schedule? = scheduleRepository.getScheduleById(id)
}
