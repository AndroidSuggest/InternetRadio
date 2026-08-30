package com.armanmaurya.internetradio.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ITunesSearchResponse(
    @SerializedName("resultCount") val resultCount: Int,
    @SerializedName("results") val results: List<ITunesTrack>?
)

data class ITunesTrack(
    @SerializedName("artworkUrl100") val artworkUrl100: String?,
    @SerializedName("trackName") val trackName: String?,
    @SerializedName("artistName") val artistName: String?
)
