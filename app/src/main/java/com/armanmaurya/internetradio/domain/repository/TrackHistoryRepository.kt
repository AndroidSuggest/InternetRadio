package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import kotlinx.coroutines.flow.Flow

interface TrackHistoryRepository {
    suspend fun logTrack(stationUuid: String, trackTitle: String): Long?
    suspend fun updateTrackMetadata(id: Long, newTrackTitle: String, coverArtUrl: String?)
    fun getTrackHistory(stationUuid: String): Flow<List<TrackHistoryEntity>>
    suspend fun updateCoverArt(stationUuid: String, trackTitle: String, coverArtUrl: String)
}
