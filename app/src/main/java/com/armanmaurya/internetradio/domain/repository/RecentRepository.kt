package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.data.model.RadioStation
import kotlinx.coroutines.flow.Flow

interface RecentRepository {
    fun getAllRecent(): Flow<List<RadioStation>>
    suspend fun getStationById(stationUuid: String): RadioStation?
    suspend fun addRecentStation(station: RadioStation)
    suspend fun removeRecent(stationUuid: String)
    suspend fun clearAllRecent()
}
