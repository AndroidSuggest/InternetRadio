package com.armanmaurya.internetradio.data.remote.dto

import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.utils.LanguageMapper
import com.google.gson.annotations.SerializedName

data class StationDto(
    @SerializedName("changeuuid") val changeUuid: String,
    @SerializedName("stationuuid") val stationUuid: String,
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String,
    @SerializedName("url_resolved") val urlResolved: String,
    @SerializedName("homepage") val homepage: String,
    @SerializedName("favicon") val favicon: String,
    @SerializedName("tags") val tags: String,
    @SerializedName("country") val country: String,
    @SerializedName("countrycode") val countryCode: String,
    @SerializedName("state") val state: String,
    @SerializedName("iso_3166_2") val iso3166_2: String?,
    @SerializedName("language") val language: String,
    @SerializedName("languagecodes") val languageCodes: String?,
    @SerializedName("votes") val votes: Int,
    @SerializedName("lastchangetime") val lastChangeTime: String,
    @SerializedName("lastchangetime_iso8601") val lastChangeTimeIso8601: String?,
    @SerializedName("codec") val codec: String,
    @SerializedName("bitrate") val bitrate: Int,

    @SerializedName("lastcheckok") val lastCheckOk: Int,
    @SerializedName("lastchecktime") val lastCheckTime: String,
    @SerializedName("lastchecktime_iso8601") val lastCheckTimeIso8601: String?,
    @SerializedName("lastcheckoktime") val lastCheckOkTime: String,
    @SerializedName("lastcheckoktime_iso8601") val lastCheckOkTimeIso8601: String?,
    @SerializedName("lastlocalchecktime") val lastLocalCheckTime: String,
    @SerializedName("lastlocalchecktime_iso8601") val lastLocalCheckTimeIso8601: String?,
    @SerializedName("clicktimestamp") val clickTimestamp: String,
    @SerializedName("clicktimestamp_iso8601") val clickTimestampIso8601: String?,
    @SerializedName("clickcount") val clickCount: Int,
    @SerializedName("clicktrend") val clickTrend: Int,
    @SerializedName("ssl_error") val sslError: Int,
    @SerializedName("geo_lat") val geoLat: Double?,
    @SerializedName("geo_long") val geoLong: Double?,
    @SerializedName("geo_distance") val geoDistance: Double?,
    @SerializedName("has_extended_info") val hasExtendedInfo: Boolean?
)

fun StationDto.toDomain(): RadioStation {
    val apiLanguageCodes = languageCodes?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
    
    val resolvedLanguageCodes = if (!apiLanguageCodes.isNullOrEmpty()) {
        apiLanguageCodes.mapNotNull { code ->
            com.neovisionaries.i18n.LanguageAlpha3Code.getByCodeIgnoreCase(code)?.alpha3B?.name
                ?: com.neovisionaries.i18n.LanguageCode.getByCodeIgnoreCase(code)?.alpha3?.alpha3B?.name
                ?: code
        }.distinct()
    } else if (language.isNotBlank()) {
        LanguageMapper.getCodesFromNameString(language).mapNotNull { code ->
            com.neovisionaries.i18n.LanguageAlpha3Code.getByCodeIgnoreCase(code)?.alpha3B?.name
                ?: com.neovisionaries.i18n.LanguageCode.getByCodeIgnoreCase(code)?.alpha3?.alpha3B?.name
                ?: code
        }.distinct()
    } else {
        emptyList()
    }

    return RadioStation(
        changeUuid = changeUuid,
        stationUuid = stationUuid,
        name = name.trim(),
        url = url,
        urlResolved = urlResolved,
        homepage = homepage,
        favicon = favicon,
        tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
        country = country,
        countryCode = countryCode,
        state = state,
        iso3166_2 = iso3166_2,
        language = language,
        languageCodes = resolvedLanguageCodes,
        votes = votes,
        lastChangeTime = lastChangeTime,
        codec = codec,
        bitrate = bitrate,

        lastCheckOk = lastCheckOk != 0,
        lastCheckTime = lastCheckTime,
        lastCheckOkTime = lastCheckOkTime,
        lastLocalCheckTime = lastLocalCheckTime,
        clickTimestamp = clickTimestamp,
        clickCount = clickCount,
        clickTrend = clickTrend,
        sslError = sslError != 0,
        geoLat = geoLat,
        geoLong = geoLong,
        geoDistance = geoDistance,
        hasExtendedInfo = hasExtendedInfo == true
    )
}
