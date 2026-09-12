package com.armanmaurya.internetradio.recording.engine.recorder

import com.armanmaurya.internetradio.recording.engine.RecordingSink

interface StreamRecorder {
    /**
     * Connects to [url], writes incoming audio data into [sink],
     * and reports progress via [onBytesWritten].
     */
    suspend fun record(
        url: String,
        sink: RecordingSink,
        onBytesWritten: (bytes: Long) -> Unit
    )
}
