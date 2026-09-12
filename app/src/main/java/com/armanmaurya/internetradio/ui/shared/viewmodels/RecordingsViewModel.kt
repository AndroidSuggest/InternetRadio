package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.model.RecordingFolder
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.RecordingRepository
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.player.PlaybackService
import com.armanmaurya.internetradio.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _folders = MutableStateFlow<List<RecordingFolder>>(emptyList())
    val folders: StateFlow<List<RecordingFolder>> = _folders.asStateFlow()

    val libraryStationUuids: StateFlow<Set<String>> = libraryRepository.getAllStations()
        .map { stations -> stations.map { it.stationUuid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        viewModelScope.launch {
            recordingRepository.recordingsChangedEvent.collect {
                loadFolders()
            }
        }
    }

    fun loadFolders() {
        viewModelScope.launch {
            _folders.value = recordingRepository.getRecordingFolders()
        }
    }

    fun deleteRecording(recording: com.armanmaurya.internetradio.domain.model.RecordingFile) {
        viewModelScope.launch {
            recordingRepository.deleteRecording(recording)
        }
    }

    fun deleteRecordings(recordings: List<com.armanmaurya.internetradio.domain.model.RecordingFile>) {
        viewModelScope.launch {
            recordingRepository.deleteRecordings(recordings)
        }
    }

    fun deleteFolders(stationNames: List<String>) {
        viewModelScope.launch {
            recordingRepository.deleteRecordingFolders(stationNames)
        }
    }

    fun toggleLibrary(station: RadioStation) {
        viewModelScope.launch {
            if (libraryStationUuids.value.contains(station.stationUuid)) {
                libraryRepository.removeStationFromLibrary(station.stationUuid)
            } else {
                libraryRepository.addStationToLibrary(station)
            }
        }
    }
}
