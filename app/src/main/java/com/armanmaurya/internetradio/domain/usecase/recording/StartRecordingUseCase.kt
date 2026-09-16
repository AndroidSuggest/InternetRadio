package com.armanmaurya.internetradio.domain.usecase.recording

import com.armanmaurya.internetradio.domain.controller.RecordingController
import com.armanmaurya.internetradio.domain.model.RadioStation
import javax.inject.Inject

class StartRecordingUseCase @Inject constructor(
    private val recordingController: RecordingController
) {
    operator fun invoke(station: RadioStation) {
        recordingController.startRecording(station)
    }
}
