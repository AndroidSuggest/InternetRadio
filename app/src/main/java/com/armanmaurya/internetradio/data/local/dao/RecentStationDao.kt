package com.armanmaurya.internetradio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.armanmaurya.internetradio.data.local.entity.RecentStationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentStationDao {
    @Query("SELECT * FROM recent_stations ORDER BY lastPlayedAt DESC LIMIT 50")
    fun getAllRecent(): Flow<List<RecentStationEntity>>

    @Query("SELECT * FROM recent_stations WHERE stationUuid = :stationUuid LIMIT 1")
    suspend fun getStationById(stationUuid: String): RecentStationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(station: RecentStationEntity)

    @Query("DELETE FROM recent_stations WHERE stationUuid = :stationUuid")
    suspend fun deleteRecent(stationUuid: String)

    @Query("DELETE FROM recent_stations")
    suspend fun clearAllRecent()

    @Query("UPDATE recent_stations SET stationUuid = :newUuid WHERE stationUuid = :oldUuid")
    suspend fun updateStationUuid(oldUuid: String, newUuid: String)
}
