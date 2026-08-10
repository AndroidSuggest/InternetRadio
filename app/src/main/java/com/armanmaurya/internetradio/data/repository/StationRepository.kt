package com.armanmaurya.internetradio.data.repository

import android.content.Context
import com.armanmaurya.internetradio.data.model.Country
import com.armanmaurya.internetradio.data.model.Language
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.model.Tag
import com.armanmaurya.internetradio.data.remote.AddStationResponse
import com.armanmaurya.internetradio.data.remote.RadioBrowserApi
import com.armanmaurya.internetradio.data.remote.dto.toDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationRepository @Inject constructor(
    private val api: RadioBrowserApi,
    @ApplicationContext private val context: Context
) {

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
    ): Result<List<RadioStation>> =
        runCatching {
            api.advancedSearch(
                name = name,
                nameExact = nameExact,
                country = country,
                countryExact = countryExact,
                countryCode = countryCode,
                state = state,
                stateExact = stateExact,
                language = language,
                languageExact = languageExact,
                tag = tag,
                tagExact = tagExact,
                tagList = tagList,
                codec = codec,
                bitrateMin = bitrateMin,
                bitrateMax = bitrateMax,
                hasExtendedInfo = hasExtendedInfo,
                isHttps = isHttps,
                order = order,
                reverse = reverse,
                limit = limit,
                offset = offset,
                hideBroken = hideBroken
            ).map { it.toDomain() }
        }

    suspend fun getCountries(): Result<List<Country>> =
        runCatching {
            api.getCountries()
                .map { it.toDomain() }
                .filter { it.isoCode.isNotBlank() }
                .sortedByDescending { it.stationCount }
        }

    suspend fun getLanguages(filter: String? = null): Result<List<Language>> =
        runCatching {
            if (filter.isNullOrBlank()) {
                api.getLanguages(order = "stationcount", reverse = true)
                    .map { it.toDomain() }
            } else {
                api.getLanguagesFiltered(filter = filter, order = "stationcount", reverse = true)
                    .map { it.toDomain() }
            }
        }

    suspend fun getTags(filter: String? = null): Result<List<Tag>> =
        runCatching {
            if (filter.isNullOrBlank()) {
                api.getTags(order = "stationcount", reverse = true)
                    .map { it.toDomain() }
            } else {
                api.getTagsFiltered(filter = filter, order = "stationcount", reverse = true)
                    .map { it.toDomain() }
            }
        }

    suspend fun getCurrentCountryCode(): Result<String> =
        runCatching {
            val countryCode = context.resources.configuration.locales[0].country
            if (countryCode.isBlank()) {
                throw IllegalStateException("Country code not available in locale")
            }
            countryCode
        }

    /**
     * Should be called every time a user starts playing a station.
     * radio-browser.info uses this to rank station popularity.
     * Failures are silently ignored — this is a fire-and-forget call.
     */
    suspend fun registerClick(stationUuid: String) {
        runCatching { api.clickStation(stationUuid) }
    }

    suspend fun getStationsByUuid(uuids: List<String>): Result<List<RadioStation>> =
        runCatching {
            api.getStationsByUuid(uuids.joinToString(",")).map { it.toDomain() }
        }

    suspend fun getStationsByUrl(url: String): Result<List<RadioStation>> =
        runCatching {
            api.searchByUrl(url).map { it.toDomain() }
        }

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
    ): Result<AddStationResponse> = runCatching {
        api.addStation(
            name = name,
            url = url,
            homepage = homepage,
            favicon = favicon,
            countryCode = countryCode,
            iso31662 = iso31662,
            languageCodes = languageCodes,
            tags = tags,
            geoLat = geoLat,
            geoLong = geoLong
        )
    }
}