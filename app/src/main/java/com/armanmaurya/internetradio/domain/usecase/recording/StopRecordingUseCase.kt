package com.armanmaurya.internetradio.domain.usecase.recording

import com.armanmaurya.internetradio.domain.controller.RecordingController
import javax.inject.Inject

class StopRecordingUseCase @Inject constructor(
    private val recordingController: RecordingController
) {
    operator fun invoke(stationUuid: String) {
        recordingController.stopRecording(stationUuid)
    }
}
