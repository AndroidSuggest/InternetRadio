package com.armanmaurya.internetradio.data.model

data class LrcLine(val timestampMs: Long, val text: String)

sealed interface LyricsState {
    object Loading : LyricsState
    object NotAvailable : LyricsState
    data class Success(val plainLyrics: String?, val syncedLyrics: List<LrcLine>?) : LyricsState
}
