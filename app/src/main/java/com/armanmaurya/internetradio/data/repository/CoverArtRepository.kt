package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.remote.ITunesApiService
import com.armanmaurya.internetradio.utils.TrackSanitizer
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
            val cleanTrack = TrackSanitizer.sanitizeTrackInfo(trackName)
            val cleanArtist = artistName?.let { TrackSanitizer.sanitizeTrackInfo(it) }
            val term = if (!cleanArtist.isNullOrBlank()) "$cleanTrack $cleanArtist" else cleanTrack
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
