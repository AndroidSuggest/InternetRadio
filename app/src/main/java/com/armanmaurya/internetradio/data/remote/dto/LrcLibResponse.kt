package com.armanmaurya.internetradio.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LrcLibResponse(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val duration: Double,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?
)
