package com.armanmaurya.internetradio.data.remote

import com.armanmaurya.internetradio.data.remote.dto.LrcLibResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface LrcLibApi {
    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String
    ): List<LrcLibResponse>
}
