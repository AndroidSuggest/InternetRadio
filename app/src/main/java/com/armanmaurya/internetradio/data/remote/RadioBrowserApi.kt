package com.armanmaurya.internetradio.data.remote

import com.armanmaurya.internetradio.data.remote.dto.CountryDto
import com.armanmaurya.internetradio.data.remote.dto.LanguageDto
import com.armanmaurya.internetradio.data.remote.dto.StationDto
import com.armanmaurya.internetradio.data.remote.dto.TagDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RadioBrowserApi {

    @GET("json/countries")
    suspend fun getCountries(): List<CountryDto>

    @GET("json/languages")
    suspend fun getLanguages(
        @Query("order") order: String = "name",
        @Query("reverse") reverse: Boolean = false,
        @Query("hidebroken") hideBroken: Boolean = false,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100000,
    ): List<LanguageDto>

    @GET("json/languages/{filter}")
    suspend fun getLanguagesFiltered(
        @Path("filter") filter: String,
        @Query("order") order: String = "name",
        @Query("reverse") reverse: Boolean = false,
        @Query("hidebroken") hideBroken: Boolean = false,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100000,
    ): List<LanguageDto>

    @GET("json/tags")
    suspend fun getTags(
        @Query("order") order: String = "name",
        @Query("reverse") reverse: Boolean = false,
        @Query("hidebroken") hideBroken: Boolean = false,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100000,
    ): List<TagDto>

    @GET("json/tags/{filter}")
    suspend fun getTagsFiltered(
        @Path("filter") filter: String,
        @Query("order") order: String = "name",
        @Query("reverse") reverse: Boolean = false,
        @Query("hidebroken") hideBroken: Boolean = false,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100000,
    ): List<TagDto>

    @GET("json/stations/search")
    suspend fun advancedSearch(
        @Query("name") name: String? = null,
        @Query("nameExact") nameExact: Boolean? = null,
        @Query("country") country: String? = null,
        @Query("countryExact") countryExact: Boolean? = null,
        @Query("countrycode") countryCode: String? = null,
        @Query("state") state: String? = null,
        @Query("stateExact") stateExact: Boolean? = null,
        @Query("language") language: String? = null,
        @Query("languageExact") languageExact: Boolean? = null,
        @Query("tag") tag: String? = null,
        @Query("tagExact") tagExact: Boolean? = null,
        @Query("tagList") tagList: String? = null,
        @Query("codec") codec: String? = null,
        @Query("bitrateMin") bitrateMin: Int? = null,
        @Query("bitrateMax") bitrateMax: Int? = null,
        @Query("has_geo_info") hasGeoInfo: Boolean? = null,
        @Query("has_extended_info") hasExtendedInfo: Boolean? = null,
        @Query("is_https") isHttps: Boolean? = null,
        @Query("order") order: String? = null,
        @Query("reverse") reverse: Boolean? = null,
        @Query("offset") offset: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("hidebroken") hideBroken: Boolean? = null,
    ): List<StationDto>

    @GET("json/url/{stationuuid}")
    suspend fun clickStation(
        @retrofit2.http.Path("stationuuid") stationUuid: String,
    ): StationClickResponse

    @GET("json/stations/byuuid")
    suspend fun getStationsByUuid(
        @Query("uuids") uuids: String
    ): List<StationDto>

    @GET("json/stations/byurl")
    suspend fun searchByUrl(
        @Query("url") url: String
    ): List<StationDto>

    @FormUrlEncoded
    @POST("json/add")
    suspend fun addStation(
        @Field("name") name: String,
        @Field("url") url: String,
        @Field("homepage") homepage: String? = null,
        @Field("favicon") favicon: String? = null,
        @Field("countrycode") countryCode: String? = null,
        @Field("iso_3166_2") iso31662: String? = null,
        @Field("languagecodes") languageCodes: String? = null,
        @Field("tags") tags: String? = null,
        @Field("geo_lat") geoLat: Double? = null,
        @Field("geo_long") geoLong: Double? = null,
    ): AddStationResponse
}

data class StationClickResponse(
    val ok: Boolean,
    val message: String,
    val url: String,
)

data class AddStationResponse(
    val ok: Boolean,
    val message: String,
    val uuid: String
)