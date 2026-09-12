package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.RecentStationDao
import com.armanmaurya.internetradio.data.local.entity.toDomain
import com.armanmaurya.internetradio.data.local.entity.toRecentEntity
import com.armanmaurya.internetradio.domain.model.RadioStation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.armanmaurya.internetradio.domain.repository.RecentRepository

@Singleton
class RecentRepositoryImpl @Inject constructor(
    private val recentStationDao: RecentStationDao
) : RecentRepository {
    override fun getAllRecent(): Flow<List<RadioStation>> =
        recentStationDao.getAllRecent().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getStationById(stationUuid: String): RadioStation? {
        return recentStationDao.getStationById(stationUuid)?.toDomain()
    }

    override suspend fun addRecentStation(station: RadioStation) {
        recentStationDao.insertOrUpdate(station.toRecentEntity())
    }

    override suspend fun removeRecent(stationUuid: String) {
        recentStationDao.deleteRecent(stationUuid)
    }

    override suspend fun clearAllRecent() {
        recentStationDao.clearAllRecent()
    }
}
