package com.armanmaurya.internetradio.player

import android.content.Context
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.RecordingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository,
    private val okHttpClient: OkHttpClient,
    private val fileSystemFacade: com.armanmaurya.internetradio.core.system.FileSystemFacade
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeSessions = mutableMapOf<String, RecordingSession>()
    
    private val _sessionsFlow = MutableStateFlow<Map<String, RecordingSession>>(emptyMap())
    val sessionsFlow: StateFlow<Map<String, RecordingSession>> = _sessionsFlow.asStateFlow()
    
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _recordingSavedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recordingSavedEvent: SharedFlow<Unit> = _recordingSavedEvent.asSharedFlow()

    fun updateAmplitude(rms: Float) {
        _amplitude.value = rms
    }

    fun startRecording(station: RadioStation): Boolean {
        if (activeSessions.containsKey(station.stationUuid)) return false

        val session = RecordingSession(
            station = station,
            context = context,
            okHttpClient = okHttpClient,
            scope = scope,
            fileSystemFacade = fileSystemFacade,
            onStopped = ::onSessionStopped
        )
        activeSessions[station.stationUuid] = session
        _sessionsFlow.update { activeSessions.toMap() }
        session.start()
        return true
    }

    fun stopRecording(uuid: String) {
        activeSessions[uuid]?.stop()
    }

    fun stopAllRecordings() {
        activeSessions.values.forEach { it.stop() }
    }

    private fun onSessionStopped(uuid: String, bytesWritten: Long) {
        activeSessions.remove(uuid)
        _sessionsFlow.update { activeSessions.toMap() }
        if (bytesWritten > 0) {
            _recordingSavedEvent.tryEmit(Unit)
        }
        recordingRepository.notifyRecordingsChanged()
    }
}