package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.local.dao.LibraryStationDao
import com.armanmaurya.internetradio.data.local.entity.LibraryStationEntity
import com.armanmaurya.internetradio.data.local.entity.toDomain
import com.armanmaurya.internetradio.data.local.entity.toLibraryEntity
import com.armanmaurya.internetradio.data.model.RadioStation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import com.armanmaurya.internetradio.data.local.dao.RecentStationDao
import com.armanmaurya.internetradio.data.local.dao.ScheduleDao
import com.armanmaurya.internetradio.data.remote.RadioBrowserApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val libraryStationDao: LibraryStationDao,
    private val recentStationDao: RecentStationDao,
    private val scheduleDao: ScheduleDao,
    private val radioBrowserApi: RadioBrowserApi
) {
    fun getAllStations(): Flow<List<RadioStation>> {
        return libraryStationDao.getAllStations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getStationsByOldestAdded(): Flow<List<RadioStation>> {
        return libraryStationDao.getStationsByOldestAdded().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getStationsByName(): Flow<List<RadioStation>> {
        return libraryStationDao.getStationsByName().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getStationsByNameDescending(): Flow<List<RadioStation>> {
        return libraryStationDao.getStationsByNameDescending().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getStationsByRecentlyPlayed(): Flow<List<RadioStation>> {
        return libraryStationDao.getStationsByRecentlyPlayed().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getStationsByLeastRecentlyPlayed(): Flow<List<RadioStation>> {
        return libraryStationDao.getStationsByLeastRecentlyPlayed().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getStationsByCustomOrder(): Flow<List<RadioStation>> {
        return libraryStationDao.getStationsByCustomOrder().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun updateStations(stations: List<LibraryStationEntity>) {
        libraryStationDao.updateStations(stations)
    }

    fun isStationInLibrary(stationUuid: String): Flow<Boolean> {
        return libraryStationDao.isStationInLibrary(stationUuid).map { it != 0 }
    }

    suspend fun isStationInLibraryDirect(stationUuid: String): Boolean {
        return libraryStationDao.isStationInLibraryDirect(stationUuid)
    }

    suspend fun getStationById(stationUuid: String): RadioStation? {
        return libraryStationDao.getStationById(stationUuid)?.toDomain()
    }

    suspend fun addStationToLibrary(station: RadioStation) {
        libraryStationDao.insertStation(station.toLibraryEntity())
    }

    suspend fun addCustomStation(
        name: String,
        url: String,
        favicon: String = "",
        tags: List<String> = emptyList(),
        countryCode: String = "",
        languageCodes: List<String> = emptyList(),
        homepage: String = "",
        iso31662: String? = null,
        codec: String = "unknown",
        bitrate: Int = 0
    ) {
        val station = LibraryStationEntity(
            stationUuid = UUID.randomUUID().toString(),
            name = name,
            url = url,
            urlResolved = url,
            favicon = favicon,
            tags = tags,
            countryCode = countryCode,
            languageCodes = languageCodes,
            homepage = homepage,
            iso3166_2 = iso31662,
            codec = codec,
            bitrate = bitrate,

            isCustom = true
        )
        libraryStationDao.insertStation(station)
    }
    
    suspend fun updateStation(
        stationUuid: String,
        name: String,
        url: String,
        favicon: String,
        tags: List<String>,
        countryCode: String,
        languageCodes: List<String>,
        homepage: String,
        iso31662: String?,
        codec: String,
        bitrate: Int
    ) {
        val existing = libraryStationDao.getStationById(stationUuid) ?: return
        val updated = existing.copy(
            name = name,
            url = url,
            urlResolved = url,
            favicon = favicon,
            tags = tags,
            countryCode = countryCode,
            languageCodes = languageCodes,
            homepage = homepage,
            iso3166_2 = iso31662,
            codec = codec,
            bitrate = bitrate
        )
        libraryStationDao.insertStation(updated)
    }

    suspend fun removeStationFromLibrary(stationUuid: String) {
        libraryStationDao.deleteStationById(stationUuid)
    }

    suspend fun uploadAndSaveNewStation(
        name: String,
        url: String,
        homepage: String,
        favicon: String,
        countryCode: String,
        iso31662: String?,
        languageCodes: List<String>,
        tags: List<String>,
        codec: String,
        bitrate: Int
    ): Result<String> {
        return try {
            val response = radioBrowserApi.addStation(
                name = name,
                url = url,
                homepage = homepage,
                favicon = favicon,
                countryCode = countryCode,
                iso31662 = iso31662,
                languageCodes = languageCodes.joinToString(","),
                tags = tags.joinToString(","),
            )
            if (response.ok) {
                val newUuid = response.uuid?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
                val newStation = com.armanmaurya.internetradio.data.local.entity.LibraryStationEntity(
                    stationUuid = newUuid,
                    name = name,
                    url = url,
                    urlResolved = url,
                    favicon = favicon,
                    tags = tags,
                    countryCode = countryCode,
                    languageCodes = languageCodes,
                    homepage = homepage,
                    iso3166_2 = iso31662,
                    codec = codec,
                    bitrate = bitrate,
                    isCustom = false
                )
                libraryStationDao.insertStation(newStation)
                Result.success(newUuid)
            } else {
                Result.failure(Exception(response.message ?: "Unknown API error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadExistingCustomStation(
        stationUuid: String,
        name: String,
        url: String,
        homepage: String,
        favicon: String,
        countryCode: String,
        iso31662: String?,
        languageCodes: List<String>,
        tags: List<String>,
        codec: String,
        bitrate: Int
    ): Result<String> {
        return try {
            val response = radioBrowserApi.addStation(
                name = name,
                url = url,
                homepage = homepage,
                favicon = favicon,
                countryCode = countryCode,
                iso31662 = iso31662,
                languageCodes = languageCodes.joinToString(","),
                tags = tags.joinToString(","),
            )
            if (response.ok) {
                val newUuid = response.uuid?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
                val oldUuid = stationUuid
                // Update DAOs to replace old UUID with new UUID, and mark as not custom
                libraryStationDao.updateStationUuid(oldUuid, newUuid)
                recentStationDao.updateStationUuid(oldUuid, newUuid)
                scheduleDao.updateStationUuid(oldUuid, newUuid)
                // Also update the other metadata to what they are in `station` just in case
                updateStation(
                    stationUuid = newUuid,
                    name = name,
                    url = url,
                    favicon = favicon,
                    tags = tags,
                    countryCode = countryCode,
                    languageCodes = languageCodes,
                    homepage = homepage,
                    iso31662 = iso31662,
                    codec = codec,
                    bitrate = bitrate
                )
                Result.success(newUuid)
            } else {
                Result.failure(Exception(response.message ?: "Unknown API error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Backup & Restore ---

    suspend fun getAllStationEntities(): List<LibraryStationEntity> {
        return libraryStationDao.getAllStationEntities()
    }

    suspend fun getEntityById(stationUuid: String): LibraryStationEntity? {
        return libraryStationDao.getStationById(stationUuid)
    }

    suspend fun insertEntity(entity: LibraryStationEntity) {
        libraryStationDao.insertStation(entity)
    }
}
