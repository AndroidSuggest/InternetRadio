package com.armanmaurya.internetradio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.armanmaurya.internetradio.data.model.RadioStation

@Entity(tableName = "recent_stations")
data class RecentStationEntity(
    @PrimaryKey val stationUuid: String,
    val name: String,
    val url: String,
    val urlResolved: String,
    val favicon: String,
    val tags: List<String>,
    val countryCode: String,
    @androidx.room.ColumnInfo(defaultValue = "")
    val languageCodes: List<String>,
    val codec: String,
    val bitrate: Int,

    @androidx.room.ColumnInfo(defaultValue = "")
    val homepage: String = "",
    val iso3166_2: String? = null,
    val geoLat: Double? = null,
    val geoLong: Double? = null,
    val lastPlayedAt: Long = System.currentTimeMillis()
)

fun RecentStationEntity.toDomain() = RadioStation(
    changeUuid = "",
    stationUuid = stationUuid,
    name = name,
    url = url,
    urlResolved = urlResolved,
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
    hasExtendedInfo = false
)

fun RadioStation.toRecentEntity(timestamp: Long = System.currentTimeMillis()) = RecentStationEntity(
    stationUuid = stationUuid,
    name = name,
    url = url,
    urlResolved = urlResolved,
    favicon = favicon,
    tags = tags,
    countryCode = countryCode,
    languageCodes = languageCodes,
    codec = codec,
    bitrate = bitrate,

    homepage = homepage,
    iso3166_2 = iso3166_2,
    geoLat = geoLat,
    geoLong = geoLong,
    lastPlayedAt = timestamp
)
