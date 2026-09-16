package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.domain.model.RecordingFolder
import com.armanmaurya.internetradio.domain.model.RecordingSession
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.RecordingRepository
import com.armanmaurya.internetradio.domain.usecase.recording.DeleteRecordingUseCase
import com.armanmaurya.internetradio.domain.usecase.recording.GetActiveRecordingsUseCase
import com.armanmaurya.internetradio.domain.usecase.recording.GetRecordingFoldersUseCase
import com.armanmaurya.internetradio.domain.usecase.recording.StopRecordingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val getRecordingFoldersUseCase: GetRecordingFoldersUseCase,
    private val deleteRecordingUseCase: DeleteRecordingUseCase,
    private val getActiveRecordingsUseCase: GetActiveRecordingsUseCase,
    private val stopRecordingUseCase: StopRecordingUseCase,
    private val recordingRepository: RecordingRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _folders = MutableStateFlow<List<RecordingFolder>>(emptyList())
    val folders: StateFlow<List<RecordingFolder>> = _folders.asStateFlow()

    val activeSessions: StateFlow<Map<String, RecordingSession>> = getActiveRecordingsUseCase()

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
            _folders.value = getRecordingFoldersUseCase()
        }
    }

    fun stopRecording(stationUuid: String) {
        stopRecordingUseCase(stationUuid)
    }

    fun deleteRecording(recording: RecordingFile) {
        viewModelScope.launch {
            deleteRecordingUseCase.deleteFile(recording)
        }
    }

    fun deleteRecordings(recordings: List<RecordingFile>) {
        viewModelScope.launch {
            deleteRecordingUseCase.deleteFiles(recordings)
        }
    }

    fun deleteFolders(stationNames: List<String>) {
        viewModelScope.launch {
            deleteRecordingUseCase.deleteFolders(stationNames)
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
