package com.armanmaurya.internetradio.domain.controller

import com.armanmaurya.internetradio.domain.model.CastDevice
import com.armanmaurya.internetradio.domain.model.CastPlaybackState
import kotlinx.coroutines.flow.StateFlow

interface CastController {
    val discoveredDevices: StateFlow<List<CastDevice>>
    val connectedDevice: StateFlow<CastDevice?>
    val playbackState: StateFlow<CastPlaybackState>
    val volume: StateFlow<Double>
    val time: StateFlow<Double>

    fun connectToDevice(device: CastDevice)
    fun disconnect()
    fun load(
        url: String,
        contentType: String = "audio/mpeg",
        resumePosition: Double = 0.0,
        title: String? = null,
        thumbnailUrl: String? = null
    )
    fun play()
    fun pause()
    fun stop()
    fun seek(time: Double)
    fun setVolume(volume: Double)
}
