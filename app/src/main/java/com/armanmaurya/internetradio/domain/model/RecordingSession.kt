package com.armanmaurya.internetradio.domain.model

import kotlinx.coroutines.flow.StateFlow

data class RecordingSession(
    val station: RadioStation,
    val startTimeMs: Long = System.currentTimeMillis(),
    val durationSeconds: StateFlow<Long>,
    @Volatile var bytesWritten: Long = 0L
)
