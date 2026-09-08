package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.model.GithubRelease
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import com.armanmaurya.internetradio.domain.repository.UpdateRepository

class UpdateRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient
) : UpdateRepository {
    private val gson = Gson()

    override suspend fun getLatestRelease(isNightly: Boolean): GithubRelease? = withContext(Dispatchers.IO) {
        try {
            val url = if (isNightly) {
                "https://api.github.com/repos/armanmaurya/internetradio/releases"
            } else {
                "https://api.github.com/repos/armanmaurya/internetradio/releases/latest"
            }

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { bodyString ->
                    if (isNightly) {
                        val releases = gson.fromJson(bodyString, Array<GithubRelease>::class.java)
                        return@let releases.firstOrNull { it.prerelease || it.tag_name.contains("nightly", true) }
                    } else {
                        return@let gson.fromJson(bodyString, GithubRelease::class.java)
                    }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
