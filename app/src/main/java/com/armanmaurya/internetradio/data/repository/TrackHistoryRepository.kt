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
    suspend fun logTrack(stationUuid: String, trackTitle: String) = withContext(Dispatchers.IO) {
        // Prevent duplicate consecutive inserts for the same track
        val latestTrack = trackHistoryDao.getLatestTrackForStation(stationUuid)
        if (latestTrack?.trackTitle != trackTitle) {
            val newTrack = TrackHistoryEntity(
                stationUuid = stationUuid,
                trackTitle = trackTitle,
                timestamp = System.currentTimeMillis()
            )
            trackHistoryDao.insert(newTrack)
            
            // Cleanup old tracks based on the user's limit setting
            val limit = settingsRepository.appPreferencesFlow.first().trackHistoryLimit
            trackHistoryDao.cleanupOldTracksForStation(stationUuid, keepCount = limit)
        }
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
