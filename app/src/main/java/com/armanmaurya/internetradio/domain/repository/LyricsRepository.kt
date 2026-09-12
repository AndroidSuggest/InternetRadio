package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.domain.model.LyricsState
import kotlinx.coroutines.flow.Flow

interface LyricsRepository {
    fun getLyricsForTrack(trackName: String, artistName: String? = null): Flow<LyricsState>
}
