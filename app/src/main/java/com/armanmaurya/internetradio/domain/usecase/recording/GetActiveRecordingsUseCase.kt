package com.armanmaurya.internetradio.domain.usecase.recording

import com.armanmaurya.internetradio.domain.controller.RecordingController
import com.armanmaurya.internetradio.domain.model.RecordingSession
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class GetActiveRecordingsUseCase @Inject constructor(
    private val recordingController: RecordingController
) {
    operator fun invoke(): StateFlow<Map<String, RecordingSession>> =
        recordingController.activeSessions
}
