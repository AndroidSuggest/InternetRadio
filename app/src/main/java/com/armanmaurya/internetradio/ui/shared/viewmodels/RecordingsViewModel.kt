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

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    val activeSessions: StateFlow<Map<String, RecordingSession>> = getActiveRecordingsUseCase()

    val libraryStationUuids: StateFlow<Set<String>> = libraryRepository.getAllStations()
        .map { stations -> stations.map { it.stationUuid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _selectedStationName = MutableStateFlow<String?>(null)
    val selectedStationName: StateFlow<String?> = _selectedStationName.asStateFlow()

    private val _expandedRecordingUri = MutableStateFlow<String?>(null)
    val expandedRecordingUri: StateFlow<String?> = _expandedRecordingUri.asStateFlow()

    init {
        loadFolders()
        viewModelScope.launch {
            recordingRepository.recordingsChangedEvent.collect {
                loadFolders()
            }
        }
    }

    fun selectStation(stationName: String?) {
        _selectedStationName.value = stationName
        _expandedRecordingUri.value = null
    }

    fun setExpandedRecordingUri(uri: String?) {
        _expandedRecordingUri.value = uri
    }

    fun loadFolders() {
        viewModelScope.launch {
            val loadedFolders = getRecordingFoldersUseCase()
            _folders.value = loadedFolders
            _isLoaded.value = true
            val currentSelected = _selectedStationName.value
            if (currentSelected != null && loadedFolders.none { it.stationName == currentSelected }) {
                _selectedStationName.value = null
                _expandedRecordingUri.value = null
            }
        }
    }

    fun stopRecording(stationUuid: String) {
        stopRecordingUseCase(stationUuid)
    }

    fun deleteRecording(recording: RecordingFile) {
        viewModelScope.launch {
            deleteRecordingUseCase.deleteFile(recording)
            if (_expandedRecordingUri.value == recording.uri.toString()) {
                _expandedRecordingUri.value = null
            }
        }
    }

    fun deleteRecordings(recordings: List<RecordingFile>) {
        viewModelScope.launch {
            deleteRecordingUseCase.deleteFiles(recordings)
            val deletedUris = recordings.map { it.uri.toString() }.toSet()
            if (_expandedRecordingUri.value in deletedUris) {
                _expandedRecordingUri.value = null
            }
        }
    }

    fun deleteFolders(stationNames: List<String>) {
        viewModelScope.launch {
            deleteRecordingUseCase.deleteFolders(stationNames)
            if (_selectedStationName.value in stationNames) {
                _selectedStationName.value = null
                _expandedRecordingUri.value = null
            }
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
