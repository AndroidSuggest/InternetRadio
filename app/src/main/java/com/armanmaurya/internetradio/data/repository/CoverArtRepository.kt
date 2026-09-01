package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.remote.ITunesApiService
import com.armanmaurya.internetradio.core.utils.TrackSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class TrackMetadata(
    val coverArtUrl: String?,
    val trackName: String?,
    val artistName: String?
)

@Singleton
class CoverArtRepository @Inject constructor(
    private val apiService: ITunesApiService
) {
    suspend fun getTrackMetadata(trackName: String, artistName: String?): TrackMetadata? = withContext(Dispatchers.IO) {
        try {
            val cleanTrack = TrackSanitizer.sanitizeTrackInfo(trackName)
            val cleanArtist = artistName?.let { TrackSanitizer.sanitizeTrackInfo(it) }
            val term = if (!cleanArtist.isNullOrBlank()) "$cleanTrack $cleanArtist" else cleanTrack
            val response = apiService.searchTrack(term = term)
            
            val track = response.results?.firstOrNull() ?: return@withContext null
            
            // The API returns a 100x100 thumbnail. We can request a much higher quality 600x600 
            // image simply by altering the filename in the URL.
            val coverArtUrl = track.artworkUrl100?.replace("100x100bb.jpg", "600x600bb.jpg")
            
            TrackMetadata(
                coverArtUrl = coverArtUrl,
                trackName = track.trackName,
                artistName = track.artistName
            )
        } catch (e: Exception) {
            null
        }
    }
}
