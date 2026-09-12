package com.armanmaurya.internetradio.data.remote.dto

import com.armanmaurya.internetradio.domain.model.Tag
import com.google.gson.annotations.SerializedName

data class TagDto(
    val name: String,
    @SerializedName("stationcount")
    val stationCount: Int
)

fun TagDto.toDomain(): Tag = Tag(
    name = name,
    stationCount = stationCount
)
