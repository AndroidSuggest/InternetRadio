package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.model.AppPreferences
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.Schedule
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.domain.usecase.schedule.DeleteScheduleUseCase
import com.armanmaurya.internetradio.domain.usecase.schedule.GetSchedulesUseCase
import com.armanmaurya.internetradio.domain.usecase.schedule.SaveScheduleUseCase
import com.armanmaurya.internetradio.domain.usecase.schedule.ToggleScheduleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SchedulesViewModel @Inject constructor(
    private val getSchedulesUseCase: GetSchedulesUseCase,
    private val saveScheduleUseCase: SaveScheduleUseCase,
    private val toggleScheduleUseCase: ToggleScheduleUseCase,
    private val deleteScheduleUseCase: DeleteScheduleUseCase,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val appPreferences = settingsRepository.appPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppPreferences()
        )

    val schedules: StateFlow<List<Schedule>?> = getSchedulesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val libraryStations: StateFlow<List<RadioStation>?> = libraryRepository.getAllStations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun toggleSchedule(schedule: Schedule, isEnabled: Boolean) {
        viewModelScope.launch {
            toggleScheduleUseCase(schedule, isEnabled)
        }
    }

    fun deleteSchedule(schedule: Schedule) {
        viewModelScope.launch {
            deleteScheduleUseCase(schedule)
        }
    }

    fun saveSchedule(schedule: Schedule) {
        viewModelScope.launch {
            saveScheduleUseCase(schedule)
        }
    }
}
