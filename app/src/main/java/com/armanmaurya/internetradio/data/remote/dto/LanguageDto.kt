package com.armanmaurya.internetradio.data.remote.dto

import com.armanmaurya.internetradio.domain.model.Language
import com.google.gson.annotations.SerializedName

data class LanguageDto(
    val name: String,
    @SerializedName("iso_639")
    val isoCode: String?,
    @SerializedName("stationcount")
    val stationCount: Int
)

fun LanguageDto.toDomain(): Language = Language(
    name = name,
    isoCode = isoCode,
    stationCount = stationCount
)
