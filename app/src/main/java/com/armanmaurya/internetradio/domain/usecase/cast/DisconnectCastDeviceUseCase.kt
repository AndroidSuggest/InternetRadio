package com.armanmaurya.internetradio.domain.usecase.cast

import com.armanmaurya.internetradio.domain.controller.CastController
import javax.inject.Inject

class DisconnectCastDeviceUseCase @Inject constructor(
    private val castController: CastController
) {
    operator fun invoke() {
        castController.disconnect()
    }
}
