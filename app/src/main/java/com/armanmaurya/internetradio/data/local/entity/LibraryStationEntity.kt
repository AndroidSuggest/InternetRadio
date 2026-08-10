package com.armanmaurya.internetradio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.armanmaurya.internetradio.data.model.RadioStation

@Entity(tableName = "library_stations")
data class LibraryStationEntity(
    @PrimaryKey val stationUuid: String,
    val name: String,
    val url: String,
    val urlResolved: String = "",
    val favicon: String = "",
    val tags: List<String> = emptyList(),
    val countryCode: String = "",
    @androidx.room.ColumnInfo(defaultValue = "")
    val languageCodes: List<String> = emptyList(),
    val codec: String = "unknown",
    val bitrate: Int = 0,

    val isCustom: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    
    @androidx.room.ColumnInfo(defaultValue = "")
    val homepage: String = "",
    val iso3166_2: String? = null,
    val geoLat: Double? = null,
    val geoLong: Double? = null,

    @androidx.room.ColumnInfo(defaultValue = "0")
    val orderIndex: Int = 0
)

fun LibraryStationEntity.toDomain() = RadioStation(
    changeUuid = "",
    stationUuid = stationUuid,
    name = name,
    url = url,
    urlResolved = if (urlResolved.isBlank()) url else urlResolved,
    homepage = homepage,
    favicon = favicon,
    tags = tags,
    country = "",
    countryCode = countryCode,
    state = "",
    iso3166_2 = iso3166_2,
    language = "",
    languageCodes = languageCodes,
    votes = 0,
    lastChangeTime = "",
    codec = codec,
    bitrate = bitrate,

    lastCheckOk = true,
    lastCheckTime = "",
    lastCheckOkTime = "",
    lastLocalCheckTime = "",
    clickTimestamp = "",
    clickCount = 0,
    clickTrend = 0,
    sslError = false,
    geoLat = geoLat,
    geoLong = geoLong,
    geoDistance = null,
    hasExtendedInfo = false,
    isCustom = isCustom
)

fun RadioStation.toLibraryEntity(isCustom: Boolean = this.isCustom) = LibraryStationEntity(
    stationUuid = stationUuid,
    name = name,
    url = url,
    urlResolved = urlResolved ?: "",
    favicon = favicon ?: "",
    tags = tags ?: emptyList(),
    countryCode = countryCode ?: "",
    languageCodes = languageCodes ?: emptyList(),
    codec = codec ?: "unknown",
    bitrate = bitrate ?: 0,

    isCustom = isCustom,
    homepage = homepage ?: "",
    iso3166_2 = iso3166_2,
    geoLat = geoLat,
    geoLong = geoLong
)
