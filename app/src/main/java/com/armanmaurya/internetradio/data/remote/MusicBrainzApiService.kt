package com.armanmaurya.internetradio.data.remote

import com.armanmaurya.internetradio.data.remote.dto.MusicBrainzSearchResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface MusicBrainzApiService {
    @GET("recording/")
    suspend fun searchRecording(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 1,
        @Header("User-Agent") userAgent: String = "InternetRadio/1.0 ( your@email.com )"
    ): MusicBrainzSearchResponse
}
