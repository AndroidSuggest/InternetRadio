package com.armanmaurya.internetradio.data.remote

import com.armanmaurya.internetradio.data.remote.dto.ITunesSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ITunesApiService {
    @GET("search")
    suspend fun searchTrack(
        @Query("term") term: String,
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 1
    ): ITunesSearchResponse
}
