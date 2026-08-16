package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.remote.ITunesApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverArtRepository @Inject constructor(
    private val apiService: ITunesApiService
) {
    suspend fun getCoverArt(trackName: String, artistName: String?): String? = withContext(Dispatchers.IO) {
        try {
            val term = if (!artistName.isNullOrBlank()) "$trackName $artistName" else trackName
            val response = apiService.searchTrack(term = term)
            
            val track = response.results?.firstOrNull() ?: return@withContext null
            
            // The API returns a 100x100 thumbnail. We can request a much higher quality 600x600 
            // image simply by altering the filename in the URL.
            track.artworkUrl100?.replace("100x100bb.jpg", "600x600bb.jpg")
        } catch (e: Exception) {
            null
        }
    }
}
