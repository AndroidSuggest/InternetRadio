package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.data.model.LyricsState
import kotlinx.coroutines.flow.Flow

interface LyricsRepository {
    fun getLyricsForTrack(trackName: String, artistName: String? = null): Flow<LyricsState>
}
