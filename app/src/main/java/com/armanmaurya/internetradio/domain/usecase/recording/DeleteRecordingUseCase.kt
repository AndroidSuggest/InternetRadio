package com.armanmaurya.internetradio.domain.usecase.recording

import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.domain.repository.RecordingRepository
import javax.inject.Inject

class DeleteRecordingUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository
) {
    suspend fun deleteFile(recording: RecordingFile): Boolean =
        recordingRepository.deleteRecording(recording)

    suspend fun deleteFiles(recordings: List<RecordingFile>): Boolean =
        recordingRepository.deleteRecordings(recordings)

    suspend fun deleteFolders(stationNames: List<String>): Boolean =
        recordingRepository.deleteRecordingFolders(stationNames)
}
