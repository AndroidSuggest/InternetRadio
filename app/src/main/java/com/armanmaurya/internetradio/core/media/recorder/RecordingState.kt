package com.armanmaurya.internetradio.core.media.recorder

sealed interface RecordingState {
    object Idle : RecordingState
    object Initializing : RecordingState
    data class Recording(
        val bytesWritten: Long,
        val durationMs: Long
    ) : RecordingState
    data class Completed(
        val totalBytes: Long,
        val durationMs: Long
    ) : RecordingState
    data class Failed(
        val error: RecorderException
    ) : RecordingState
}
