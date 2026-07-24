package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.local.entity.ScheduleEntity
import com.armanmaurya.internetradio.data.repository.ScheduleRepository
import com.armanmaurya.internetradio.data.repository.LibraryRepository
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.player.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SchedulesViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val scheduleManager: ScheduleManager,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    val schedules: StateFlow<List<ScheduleEntity>> = scheduleRepository.getAllSchedules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val libraryStations: StateFlow<List<RadioStation>> = libraryRepository.getAllStations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleSchedule(schedule: ScheduleEntity, isEnabled: Boolean) {
        viewModelScope.launch {
            scheduleRepository.updateScheduleStatus(schedule.id, isEnabled)
            val updated = schedule.copy(isEnabled = isEnabled)
            if (isEnabled) {
                scheduleManager.schedule(updated)
            } else {
                scheduleManager.cancel(schedule.id)
            }
        }
    }

    fun deleteSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            scheduleManager.cancel(schedule.id)
            scheduleRepository.deleteSchedule(schedule)
        }
    }

    fun saveSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            if (schedule.id == 0) {
                val id = scheduleRepository.insertSchedule(schedule)
                if (schedule.isEnabled) {
                    scheduleManager.schedule(schedule.copy(id = id.toInt()))
                }
            } else {
                scheduleRepository.updateSchedule(schedule)
                if (schedule.isEnabled) {
                    scheduleManager.schedule(schedule)
                } else {
                    scheduleManager.cancel(schedule.id)
                }
            }
        }
    }
}
