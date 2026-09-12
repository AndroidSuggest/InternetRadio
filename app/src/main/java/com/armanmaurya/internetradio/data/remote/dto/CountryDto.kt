package com.armanmaurya.internetradio.data.remote.dto

import com.armanmaurya.internetradio.domain.model.Country
import com.google.gson.annotations.SerializedName

data class CountryDto(
    val name: String,
    @SerializedName("iso_3166_1")
    val isoCode: String,
    @SerializedName("stationcount")
    val stationCount: Int
)

fun CountryDto.toDomain(): Country = Country(
    name = name,
    isoCode = isoCode,
    stationCount = stationCount
)
