package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.LibraryRepository
import com.armanmaurya.internetradio.data.repository.RecentRepository
import com.armanmaurya.internetradio.data.repository.StationRepository
import com.armanmaurya.internetradio.data.repository.TrackHistoryRepository
import com.armanmaurya.internetradio.player.PlaybackSource
import com.armanmaurya.internetradio.player.PlayerController
import com.armanmaurya.internetradio.player.RecordingManager
import com.armanmaurya.internetradio.player.SvgProxyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import org.fcast.sender_sdk.DeviceInfo

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val playerController: PlayerController,
    private val castController: com.armanmaurya.internetradio.player.CastController,
    private val libraryRepository: LibraryRepository,
    private val recentRepository: RecentRepository,
    private val stationRepository: com.armanmaurya.internetradio.data.repository.StationRepository,
    private val trackHistoryRepository: TrackHistoryRepository,
    private val recordingManager: RecordingManager,
    private val recordingRepository: com.armanmaurya.internetradio.data.repository.RecordingRepository,
    private val lyricsRepository: com.armanmaurya.internetradio.data.repository.LyricsRepository,
    retryStateTracker: com.armanmaurya.internetradio.player.RetryStateTracker
) : ViewModel() {

    val retryCountdown = retryStateTracker.retryCountdown
    val retryToastEvent = retryStateTracker.retryToastEvent

    val playbackState = playerController.playbackState
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val lyricsState = playbackState
        .map { it.currentTrack }
        .distinctUntilChanged()
        .flatMapLatest { track ->
            if (track.isNullOrBlank()) {
                flowOf(com.armanmaurya.internetradio.data.model.LyricsState.NotAvailable)
            } else {
                lyricsRepository.getLyricsForTrack(track)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.armanmaurya.internetradio.data.model.LyricsState.Loading
        )

    val activeSessions = recordingManager.sessionsFlow

    val isCurrentStationRecording = combine(playbackState.map { it.currentStation }, activeSessions) { station, sessions ->
        station != null && sessions.containsKey(station.stationUuid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentRecordingDuration = combine(playbackState.map { it.currentStation }, activeSessions) { station, sessions ->
        if (station != null) sessions[station.stationUuid] else null
    }.flatMapLatest { session ->
        session?.durationSeconds ?: kotlinx.coroutines.flow.flowOf(0L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val amplitude = recordingManager.amplitude
    val recordingSavedEvent = recordingManager.recordingSavedEvent

    val discoveredCastDevices = castController.discoveredDevices
    val connectedCastDevice = castController.connectedDevice
    val castPlaybackState = castController.playbackState
    val castVolume = castController.volume

    val currentPosition: Long
        get() = playerController.currentPosition

    init {
        playbackState
            .onEach { state ->
                if (state.isError) {
                    handlePlaybackFailure()
                }
            }
            .launchIn(viewModelScope)

        kotlinx.coroutines.flow.combine(
            playbackState.map { it.currentStation }.distinctUntilChanged { old, new -> old?.stationUuid == new?.stationUuid },
            connectedCastDevice
        ) { station, device ->
            if (station != null && device != null) {
                val proxyFavicon = if (station.favicon.endsWith(".svg", true)) {
                    SvgProxyProvider.createProxyUri(context, station.favicon)
                } else {
                    station.favicon
                }
                castController.load(
                    url = station.urlResolved,
                    contentType = "audio/mpeg",
                    title = station.name,
                    thumbnailUrl = proxyFavicon
                )
            }
        }.launchIn(viewModelScope)
        
        var wasCasting = false
        connectedCastDevice
            .onEach { device ->
                val isCasting = device != null
                if (isCasting) playerController.setVolume(0f)
                if (wasCasting && !isCasting) {
                    playerController.setVolume(1f)
                    val station = playbackState.value.currentStation
                    if (station != null) {
                        playerController.play(listOf(station), 0, playWhenReady = true)
                    }
                }
                wasCasting = isCasting
            }
            .launchIn(viewModelScope)
    }

    private fun handlePlaybackFailure() {
        val currentStation = playbackState.value.currentStation ?: return
        viewModelScope.launch {
            stationRepository.getStationsByUuid(listOf(currentStation.stationUuid))
                .onSuccess { freshStations ->
                    val freshStation = freshStations.firstOrNull() ?: return@onSuccess

                    val hasChanged = freshStation.name != currentStation.name ||
                            freshStation.url != currentStation.url ||
                            freshStation.urlResolved != currentStation.urlResolved ||
                            freshStation.favicon != currentStation.favicon ||
                            freshStation.tags != currentStation.tags ||
                            freshStation.country != currentStation.country ||
                            freshStation.language != currentStation.language ||
                            freshStation.codec != currentStation.codec ||
                            freshStation.bitrate != currentStation.bitrate

                    if (hasChanged) {
                        // Update Favorite if it exists
                        if (libraryRepository.isStationInLibraryDirect(currentStation.stationUuid)) {
                            libraryRepository.addStationToLibrary(freshStation)
                        }

                        // Update Recent
                        recentRepository.addRecentStation(freshStation)

                        // Re-trigger playback with fresh station
                        play(listOf(freshStation), 0)
                    }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val isFavorite = playbackState
        .map { it.currentStation?.stationUuid }
        .distinctUntilChanged()
        .flatMapLatest { uuid ->
            if (uuid == null) flowOf(false)
            else libraryRepository.isStationInLibrary(uuid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val trackHistory = playbackState
        .map { it.currentStation?.stationUuid }
        .distinctUntilChanged()
        .flatMapLatest { uuid ->
            if (uuid == null) flowOf(emptyList())
            else trackHistoryRepository.getTrackHistory(uuid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stationRecordings = kotlinx.coroutines.flow.combine(
        playbackState.map { it.currentStation?.name }.distinctUntilChanged(),
        recordingRepository.recordingsChangedEvent.onStart { emit(Unit) }
    ) { stationName, _ -> 
        stationName
    }.flatMapLatest { stationName ->
        if (stationName == null) flowOf(emptyList()) // Fetch recordings immediately
        else flowOf(recordingRepository.getRecordingsForStation(stationName))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun deleteRecording(recording: com.armanmaurya.internetradio.data.repository.RecordingFile) {
        viewModelScope.launch {
            recordingRepository.deleteRecording(recording)
        }
    }

    fun toggleFavorite() {
        val station = playbackState.value.currentStation ?: return
        viewModelScope.launch {
            if (isFavorite.value) {
                libraryRepository.removeStationFromLibrary(station.stationUuid)
            } else {
                libraryRepository.addStationToLibrary(station)
            }
        }
    }

    private val _permissionRequestEvent = kotlinx.coroutines.flow.MutableSharedFlow<RadioStation>()
    val permissionRequestEvent = _permissionRequestEvent.asSharedFlow()
    
    var pendingRecordingStation: RadioStation? = null

    private fun startRecordingIntent(st: RadioStation) {
        val intent = android.content.Intent(context, com.armanmaurya.internetradio.player.BackgroundRecordingService::class.java).apply {
            action = com.armanmaurya.internetradio.player.BackgroundRecordingService.ACTION_START
            putExtra("STATION_JSON", com.google.gson.Gson().toJson(st))
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun proceedWithRecording() {
        pendingRecordingStation?.let {
            startRecordingIntent(it)
            pendingRecordingStation = null
        }
    }

    fun toggleRecording(station: RadioStation? = playbackState.value.currentStation) {
        val st = station ?: return
        if (activeSessions.value.containsKey(st.stationUuid)) {
            val intent = android.content.Intent(context, com.armanmaurya.internetradio.player.BackgroundRecordingService::class.java).apply {
                action = com.armanmaurya.internetradio.player.BackgroundRecordingService.ACTION_STOP
                putExtra("UUID", st.stationUuid)
            }
            context.startService(intent)
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val permissionStatus = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                )
                if (permissionStatus == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    startRecordingIntent(st)
                } else {
                    pendingRecordingStation = st
                    viewModelScope.launch {
                        _permissionRequestEvent.emit(st)
                    }
                }
            } else {
                startRecordingIntent(st)
            }
        }
    }

    fun play(stations: List<RadioStation>, startIndex: Int, source: PlaybackSource = PlaybackSource.None) {
        val station = stations[startIndex]
        
        if (playbackState.value.currentStation?.stationUuid == station.stationUuid) {
            togglePlayPause()
            return
        }

        playerController.play(
            stations = stations,
            startIndex = startIndex,
            source = source,
            playWhenReady = true
        )
        viewModelScope.launch {
            recentRepository.addRecentStation(station)
            stationRepository.registerClick(station.stationUuid)
        }
    }

    fun playIndex(index: Int) {
        playerController.playIndex(index)
    }

    fun next() {
        playerController.next()
    }

    fun previous() {
        playerController.previous()
    }

    fun setLyricsSyncOffset(offsetMs: Long) {
        playerController.setLyricsSyncOffset(offsetMs)
    }

    fun togglePlayPause() {
        if (connectedCastDevice.value != null) {
            val state = castPlaybackState.value
            val stateName = state.toString().uppercase()
            if (stateName.contains("PLAY") || stateName.contains("BUFFER")) {
                castController.pause()
                playerController.pause()
            } else {
                castController.play()
                val station = playbackState.value.currentStation
                if (station != null) {
                    playerController.play(listOf(station), 0, playWhenReady = true)
                }
            }
        } else {
            playerController.togglePlayPause()
        }
    }

    fun stop() {
        if (connectedCastDevice.value != null) {
            castController.stop()
        }
        playerController.stop()
    }

    fun setSleepTimer(durationMillis: Long) {
        playerController.setSleepTimer(durationMillis)
    }

    fun cancelSleepTimer() {
        playerController.cancelSleepTimer()
    }

    fun connectToCastDevice(deviceInfo: DeviceInfo) {
        castController.connectToDevice(deviceInfo)
        playerController.setVolume(0f)
    }

    fun disconnectCastDevice() {
        castController.disconnect()
        playerController.setVolume(1f)
        val station = playbackState.value.currentStation
        if (station != null) {
            playerController.play(listOf(station), 0)
        }
    }

    fun setCastVolume(volume: Float) {
        castController.setVolume(volume.toDouble())
    }

    fun setVolume(volume: Float) {
        if (connectedCastDevice.value != null) {
            setCastVolume(volume)
        } else {
            playerController.setVolume(volume)
        }
    }
}