package com.armanmaurya.internetradio.domain.usecase.cast

import com.armanmaurya.internetradio.domain.controller.CastController
import com.armanmaurya.internetradio.domain.model.CastDevice
import javax.inject.Inject

class ConnectCastDeviceUseCase @Inject constructor(
    private val castController: CastController
) {
    operator fun invoke(device: CastDevice) {
        castController.connectToDevice(device)
    }
}
