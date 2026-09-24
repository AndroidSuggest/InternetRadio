package com.armanmaurya.internetradio.domain.usecase.cast

import com.armanmaurya.internetradio.domain.controller.CastController
import com.armanmaurya.internetradio.domain.model.CastDevice
import com.armanmaurya.internetradio.domain.model.CastPlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CastUseCasesTest {

    private lateinit var fakeCastController: FakeCastController
    private lateinit var connectCastDeviceUseCase: ConnectCastDeviceUseCase
    private lateinit var disconnectCastDeviceUseCase: DisconnectCastDeviceUseCase

    @Before
    fun setUp() {
        fakeCastController = FakeCastController()
        connectCastDeviceUseCase = ConnectCastDeviceUseCase(fakeCastController)
        disconnectCastDeviceUseCase = DisconnectCastDeviceUseCase(fakeCastController)
    }

    @Test
    fun connectCastDeviceUseCase_connectsToDevice() {
        val testDevice = CastDevice(
            id = "Living Room TV",
            name = "Living Room TV",
            host = "192.168.1.50",
            port = 46899
        )

        connectCastDeviceUseCase(testDevice)

        assertEquals(testDevice, fakeCastController.connectedDevice.value)
        assertEquals(listOf(testDevice), fakeCastController.connectedDevicesHistory)
    }

    @Test
    fun disconnectCastDeviceUseCase_disconnectsDevice() {
        val testDevice = CastDevice(
            id = "Living Room TV",
            name = "Living Room TV",
            host = "192.168.1.50",
            port = 46899
        )
        fakeCastController.connectToDevice(testDevice)
        assertEquals(testDevice, fakeCastController.connectedDevice.value)

        disconnectCastDeviceUseCase()

        assertNull(fakeCastController.connectedDevice.value)
        assertTrue(fakeCastController.disconnectCalled)
    }

    @Test
    fun castPlaybackState_flags_workCorrectly() {
        assertTrue(CastPlaybackState.PLAYING.isPlaying)
        assertFalse(CastPlaybackState.PLAYING.isBuffering)

        assertTrue(CastPlaybackState.BUFFERING.isBuffering)
        assertFalse(CastPlaybackState.BUFFERING.isPlaying)

        assertFalse(CastPlaybackState.IDLE.isPlaying)
        assertFalse(CastPlaybackState.IDLE.isBuffering)

        assertFalse(CastPlaybackState.PAUSED.isPlaying)
        assertFalse(CastPlaybackState.PAUSED.isBuffering)
    }

    private class FakeCastController : CastController {
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

        val connectedDevicesHistory = mutableListOf<CastDevice>()
        var disconnectCalled = false

        override fun connectToDevice(device: CastDevice) {
            connectedDevicesHistory.add(device)
            _connectedDevice.value = device
        }

        override fun disconnect() {
            disconnectCalled = true
            _connectedDevice.value = null
            _playbackState.value = CastPlaybackState.IDLE
        }

        override fun load(
            url: String,
            contentType: String,
            resumePosition: Double,
            title: String?,
            thumbnailUrl: String?
        ) {}

        override fun play() {
            _playbackState.value = CastPlaybackState.PLAYING
        }

        override fun pause() {
            _playbackState.value = CastPlaybackState.PAUSED
        }

        override fun stop() {
            _playbackState.value = CastPlaybackState.IDLE
        }

        override fun seek(time: Double) {
            _time.value = time
        }

        override fun setVolume(volume: Double) {
            _volume.value = volume
        }
    }
}
