package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.data.model.GithubRelease

interface UpdateRepository {
    suspend fun getLatestRelease(isNightly: Boolean = false): GithubRelease?
}
