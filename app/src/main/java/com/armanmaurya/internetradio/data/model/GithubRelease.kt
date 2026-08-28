package com.armanmaurya.internetradio.data.model

import androidx.annotation.Keep

@Keep
data class GithubRelease(
    val name: String?,
    val tag_name: String,
    val html_url: String,
    val body: String,
    val prerelease: Boolean = false
)
