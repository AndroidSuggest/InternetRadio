package com.armanmaurya.internetradio.recording

import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.RecordingRepository
import com.armanmaurya.internetradio.recording.engine.RecordingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class RecordingState(
    val station: RadioStation,
    val startTimeMs: Long = System.currentTimeMillis(),
    val durationSeconds: StateFlow<Long>,
    @Volatile var bytesWritten: Long = 0L
)

typealias RecordingSession = RecordingState

@Singleton
class RecordingManager @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val recordingEngine: RecordingEngine
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeRecordings = mutableMapOf<String, RecordingState>()
    private val activeJobs = mutableMapOf<String, Job>()
    
    private val _recordingsFlow = MutableStateFlow<Map<String, RecordingState>>(emptyMap())
    val recordingsFlow: StateFlow<Map<String, RecordingState>> = _recordingsFlow.asStateFlow()

    // Backward-compatibility alias
    val sessionsFlow: StateFlow<Map<String, RecordingState>> get() = recordingsFlow
    
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _recordingSavedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recordingSavedEvent: SharedFlow<Unit> = _recordingSavedEvent.asSharedFlow()

    fun updateAmplitude(rms: Float) {
        _amplitude.value = rms
    }

    fun startRecording(station: RadioStation): Boolean {
        if (activeRecordings.containsKey(station.stationUuid)) return false

        val durationFlow = MutableStateFlow(0L)
        val recordingState = RecordingState(
            station = station,
            durationSeconds = durationFlow.asStateFlow()
        )
        activeRecordings[station.stationUuid] = recordingState
        _recordingsFlow.update { activeRecordings.toMap() }

        val job = scope.launch {
            launch {
                while (isActive) {
                    delay(1000)
                    durationFlow.update { it + 1 }
                }
            }
            try {
                recordingEngine.record(
                    url = station.urlResolved,
                    title = station.name
                ) { bytes ->
                    recordingState.bytesWritten += bytes
                }
            } finally {
                onRecordingStopped(station.stationUuid, recordingState.bytesWritten)
            }
        }
        activeJobs[station.stationUuid] = job
        return true
    }

    fun stopRecording(uuid: String) {
        activeJobs.remove(uuid)?.cancel()
    }

    fun stopAllRecordings() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    private fun onRecordingStopped(uuid: String, bytesWritten: Long) {
        activeRecordings.remove(uuid)
        activeJobs.remove(uuid)
        _recordingsFlow.update { activeRecordings.toMap() }
        if (bytesWritten > 0) {
            _recordingSavedEvent.tryEmit(Unit)
        }
        recordingRepository.notifyRecordingsChanged()
    }
}
