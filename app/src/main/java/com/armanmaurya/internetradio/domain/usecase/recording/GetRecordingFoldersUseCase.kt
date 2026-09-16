package com.armanmaurya.internetradio.domain.usecase.recording

import com.armanmaurya.internetradio.domain.model.RecordingFolder
import com.armanmaurya.internetradio.domain.repository.RecordingRepository
import javax.inject.Inject

class GetRecordingFoldersUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository
) {
    suspend operator fun invoke(): List<RecordingFolder> =
        recordingRepository.getRecordingFolders()
}
