package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.repository.RecordingFolder
import com.armanmaurya.internetradio.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    private val _folders = MutableStateFlow<List<RecordingFolder>>(emptyList())
    val folders: StateFlow<List<RecordingFolder>> = _folders.asStateFlow()

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

    fun deleteRecording(recording: com.armanmaurya.internetradio.data.repository.RecordingFile) {
        viewModelScope.launch {
            recordingRepository.deleteRecording(recording)
        }
    }

    fun deleteRecordings(recordings: List<com.armanmaurya.internetradio.data.repository.RecordingFile>) {
        viewModelScope.launch {
            recordingRepository.deleteRecordings(recordings)
        }
    }

    fun deleteFolders(stationNames: List<String>) {
        viewModelScope.launch {
            recordingRepository.deleteRecordingFolders(stationNames)
        }
    }
}
