package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.TrackHistoryDao
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackHistoryRepository @Inject constructor(
    private val trackHistoryDao: TrackHistoryDao,
    private val settingsRepository: SettingsRepository
) {
    suspend fun logTrack(stationUuid: String, trackTitle: String): Long? = withContext(Dispatchers.IO) {
        val latestTrack = trackHistoryDao.getLatestTrackForStation(stationUuid)
        
        // Use rawTrackTitle for duplicate detection to avoid inserting duplicates 
        // when the stream emits the raw title but the DB was updated to the cleaned title.
        val latestRawTitle = if (latestTrack?.rawTrackTitle?.isNotBlank() == true) {
            latestTrack.rawTrackTitle
        } else {
            latestTrack?.trackTitle // Fallback for old rows before migration
        }

        if (latestRawTitle != trackTitle) {
            val newTrack = TrackHistoryEntity(
                stationUuid = stationUuid,
                trackTitle = trackTitle,
                rawTrackTitle = trackTitle,
                timestamp = System.currentTimeMillis()
            )
            val id = trackHistoryDao.insert(newTrack)
            
            // Cleanup old tracks based on the user's limit setting
            val limit = settingsRepository.appPreferencesFlow.first().trackHistoryLimit
            trackHistoryDao.cleanupOldTracksForStation(stationUuid, keepCount = limit)
            id
        } else {
            latestTrack?.id
        }
    }

    suspend fun updateTrackMetadata(id: Long, newTrackTitle: String, coverArtUrl: String?) = withContext(Dispatchers.IO) {
        trackHistoryDao.updateTrackMetadata(id, newTrackTitle, coverArtUrl)
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun getTrackHistory(stationUuid: String): Flow<List<TrackHistoryEntity>> {
        return settingsRepository.appPreferencesFlow
            .map { it.trackHistoryLimit }
            .distinctUntilChanged()
            .flatMapLatest { limit ->
                trackHistoryDao.getTrackHistoryForStation(stationUuid, limit = limit)
            }
    }

    suspend fun updateCoverArt(stationUuid: String, trackTitle: String, coverArtUrl: String) = withContext(Dispatchers.IO) {
        trackHistoryDao.updateCoverArt(stationUuid, trackTitle, coverArtUrl)
    }
}
