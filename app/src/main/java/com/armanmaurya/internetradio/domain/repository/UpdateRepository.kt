package com.armanmaurya.internetradio.domain.repository

import com.armanmaurya.internetradio.domain.model.GithubRelease

interface UpdateRepository {
    suspend fun getLatestRelease(isNightly: Boolean = false): GithubRelease?
}
