package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.data.model.Country
import com.armanmaurya.internetradio.data.model.Language
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.model.Tag
import com.armanmaurya.internetradio.data.remote.AddStationResponse

interface StationRepository {
    suspend fun filterStations(
        name: String? = null,
        nameExact: Boolean? = null,
        country: String? = null,
        countryExact: Boolean? = null,
        countryCode: String? = null,
        state: String? = null,
        stateExact: Boolean? = null,
        language: String? = null,
        languageExact: Boolean? = null,
        tag: String? = null,
        tagExact: Boolean? = null,
        tagList: String? = null,
        codec: String? = null,
        bitrateMin: Int? = null,
        bitrateMax: Int? = null,
        hasExtendedInfo: Boolean? = null,
        isHttps: Boolean? = null,
        order: String = "votes",
        reverse: Boolean = true,
        limit: Int = 40,
        offset: Int = 0,
        hideBroken: Boolean = true
    ): Result<List<RadioStation>>
    suspend fun getCountries(): Result<List<Country>>
    suspend fun getLanguages(filter: String? = null): Result<List<Language>>
    suspend fun getTags(filter: String? = null): Result<List<Tag>>
    suspend fun getCurrentCountryCode(): Result<String>
    suspend fun registerClick(stationUuid: String)
    suspend fun getStationsByUuid(uuids: List<String>): Result<List<RadioStation>>
    suspend fun getStationsByUrl(url: String): Result<List<RadioStation>>
    suspend fun addStation(
        name: String,
        url: String,
        homepage: String? = null,
        favicon: String? = null,
        countryCode: String? = null,
        iso31662: String? = null,
        languageCodes: String? = null,
        tags: String? = null,
        geoLat: Double? = null,
        geoLong: Double? = null,
    ): Result<AddStationResponse>
}
