package com.armanmaurya.internetradio.data.cast

import android.content.Context
import android.util.Log
import com.armanmaurya.internetradio.domain.controller.CastController
import com.armanmaurya.internetradio.domain.model.CastDevice
import com.armanmaurya.internetradio.domain.model.CastPlaybackState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.fcast.sender_sdk.CastContext
import org.fcast.sender_sdk.CastingDevice
import org.fcast.sender_sdk.DeviceConnectionState
import org.fcast.sender_sdk.DeviceDiscovererEventHandler
import org.fcast.sender_sdk.DeviceEventHandler
import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.KeyEvent
import org.fcast.sender_sdk.LoadRequest
import org.fcast.sender_sdk.MediaEvent
import org.fcast.sender_sdk.Metadata
import org.fcast.sender_sdk.NsdDeviceDiscoverer
import org.fcast.sender_sdk.PlaybackState
import org.fcast.sender_sdk.Source
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CastController {

    private val castContext = CastContext()
    private val deviceDiscoverer: NsdDeviceDiscoverer
    private val deviceCache = ConcurrentHashMap<String, DeviceInfo>()

    private var currentCastingDevice: CastingDevice? = null

    private val _discoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<CastDevice>> = _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<CastDevice?>(null)
    override val connectedDevice: StateFlow<CastDevice?> = _connectedDevice.asStateFlow()

    private val _playbackState = MutableStateFlow(CastPlaybackState.IDLE)
    override val playbackState: StateFlow<CastPlaybackState> = _playbackState.asStateFlow()

    private val _volume = MutableStateFlow(1.0)
    override val volume: StateFlow<Double> = _volume.asStateFlow()

    private val _time = MutableStateFlow(0.0)
    override val time: StateFlow<Double> = _time.asStateFlow()

    private var pendingLoadUrl: String? = null
    private var pendingTitle: String? = null
    private var pendingThumbnailUrl: String? = null

    private val discoveryEventHandler = object : DeviceDiscovererEventHandler {
        override fun deviceAvailable(deviceInfo: DeviceInfo) {
            deviceCache[deviceInfo.name] = deviceInfo
            updateDiscoveredDevices()
        }

        override fun deviceChanged(deviceInfo: DeviceInfo) {
            deviceCache[deviceInfo.name] = deviceInfo
            updateDiscoveredDevices()
        }

        override fun deviceRemoved(deviceName: String) {
            deviceCache.remove(deviceName)
            updateDiscoveredDevices()
        }
    }

    init {
        deviceDiscoverer = NsdDeviceDiscoverer(context, discoveryEventHandler)
    }

    private fun updateDiscoveredDevices() {
        _discoveredDevices.value = deviceCache.values
            .map { it.toDomain() }
            .sortedBy { it.name }
    }

    override fun connectToDevice(device: CastDevice) {
        val deviceInfo = deviceCache[device.id] ?: return
        if (deviceInfo.port != 0.toUShort() && deviceInfo.addresses.isNotEmpty()) {
            try {
                val newDevice = castContext.createDeviceFromInfo(deviceInfo)
                newDevice.connect(null, createDeviceEventHandler(newDevice, device), 1000u)
                currentCastingDevice = newDevice
                _connectedDevice.value = device
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to device: ${device.name}", e)
                disconnect()
            }
        }
    }

    override fun disconnect() {
        try {
            currentCastingDevice?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting casting device: ${e.message}")
        } finally {
            currentCastingDevice = null
            _connectedDevice.value = null
            _playbackState.value = CastPlaybackState.IDLE
        }
    }

    override fun load(
        url: String,
        contentType: String,
        resumePosition: Double,
        title: String?,
        thumbnailUrl: String?
    ) {
        pendingLoadUrl = url
        pendingTitle = title
        pendingThumbnailUrl = thumbnailUrl

        val metadata = if (title != null) Metadata(title, thumbnailUrl ?: "") else null

        try {
            currentCastingDevice?.load(
                LoadRequest.Url(
                    contentType = contentType,
                    url = url,
                    resumePosition = resumePosition,
                    speed = null,
                    volume = null,
                    metadata = metadata,
                    requestHeaders = null
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Load failed: ${e.message}")
            disconnect()
        }
    }

    override fun play() {
        try {
            currentCastingDevice?.resumePlayback()
        } catch (e: Exception) {
            Log.e(TAG, "Play failed: ${e.message}")
            disconnect()
        }
    }

    override fun pause() {
        try {
            currentCastingDevice?.pausePlayback()
        } catch (e: Exception) {
            Log.e(TAG, "Pause failed: ${e.message}")
            disconnect()
        }
    }

    override fun stop() {
        try {
            currentCastingDevice?.stopPlayback()
        } catch (e: Exception) {
            Log.e(TAG, "Stop failed: ${e.message}")
            disconnect()
        }
    }

    override fun seek(time: Double) {
        try {
            currentCastingDevice?.seek(time)
        } catch (e: Exception) {
            Log.e(TAG, "Seek failed: ${e.message}")
        }
    }

    override fun setVolume(volume: Double) {
        try {
            currentCastingDevice?.changeVolume(volume)
            _volume.value = volume
        } catch (e: Exception) {
            Log.e(TAG, "Set volume failed: ${e.message}")
        }
    }

    private fun createDeviceEventHandler(device: CastingDevice, domainDevice: CastDevice) =
        object : DeviceEventHandler {
            override fun connectionStateChanged(state: DeviceConnectionState) {
                Log.d(TAG, "Connection state changed: $state")
                if (state is DeviceConnectionState.Connected) {
                    _connectedDevice.value = domainDevice
                    pendingLoadUrl?.let { url ->
                        val metadata = if (pendingTitle != null) {
                            Metadata(pendingTitle!!, pendingThumbnailUrl ?: "")
                        } else null
                        try {
                            device.load(
                                LoadRequest.Url(
                                    contentType = "audio/mpeg",
                                    url = url,
                                    resumePosition = 0.0,
                                    speed = null,
                                    volume = null,
                                    metadata = metadata,
                                    requestHeaders = null
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Initial load failed after connect: ${e.message}")
                        }
                        pendingLoadUrl = null
                    }
                } else if (state !is DeviceConnectionState.Connecting) {
                    disconnect()
                }
            }

            override fun volumeChanged(volume: Double) {
                _volume.value = volume
            }

            override fun timeChanged(time: Double) {
                _time.value = time
            }

            override fun playbackStateChanged(state: PlaybackState) {
                _playbackState.value = when (state) {
                    PlaybackState.IDLE -> CastPlaybackState.IDLE
                    PlaybackState.BUFFERING -> CastPlaybackState.BUFFERING
                    PlaybackState.PLAYING -> CastPlaybackState.PLAYING
                    PlaybackState.PAUSED -> CastPlaybackState.PAUSED
                }
            }

            override fun durationChanged(duration: Double) {}
            override fun speedChanged(speed: Double) {}
            override fun sourceChanged(source: Source) {}
            override fun keyEvent(event: KeyEvent) {}
            override fun mediaEvent(event: MediaEvent) {}

            override fun playbackError(message: String) {
                Log.e(TAG, "Playback error: $message")
            }
        }

    private fun DeviceInfo.toDomain(): CastDevice {
        return CastDevice(
            id = name,
            name = name,
            host = addresses.firstOrNull()?.toString() ?: "",
            port = port.toInt()
        )
    }

    companion object {
        private const val TAG = "CastController"
    }
}
