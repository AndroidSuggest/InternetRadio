package com.armanmaurya.internetradio.data.remote.dto

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class MusicBrainzSearchResponse(
    @SerializedName("recordings") val recordings: List<MBRecording>?
)

@Keep
data class MBRecording(
    @SerializedName("releases") val releases: List<MBRelease>?
)

@Keep
data class MBRelease(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String?,
    @SerializedName("release-group") val releaseGroup: MBReleaseGroup?
)

@Keep
data class MBReleaseGroup(
    @SerializedName("id") val id: String,
    @SerializedName("primary-type") val primaryType: String?
)
