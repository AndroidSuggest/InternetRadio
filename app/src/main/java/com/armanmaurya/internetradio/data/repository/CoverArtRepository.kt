package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.remote.MusicBrainzApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverArtRepository @Inject constructor(
    private val apiService: MusicBrainzApiService
) {
    suspend fun getCoverArt(trackName: String, artistName: String?): String? = withContext(Dispatchers.IO) {
        try {
            // Strip reserved Lucene characters to prevent API crashes.
            val luceneTrack = trackName.replace(Regex("""[!*+"^:()\[\]{}\\~]"""), " ").trim()
            val luceneArtist = artistName?.replace(Regex("""[!*+"^:()\[\]{}\\~]"""), " ")?.trim()
            
            // Build a hybrid query. The boosted fielded search handles exact matches perfectly, 
            // while the unfielded search acts as a safety net for typos.
            val query = if (luceneArtist != null && luceneArtist.isNotBlank()) {
                "((recording:\"$luceneTrack\" OR release:\"$luceneTrack\") AND artist:\"$luceneArtist\")^4 OR ($luceneTrack $luceneArtist)"
            } else {
                "(recording:\"$luceneTrack\" OR release:\"$luceneTrack\")^4 OR ($luceneTrack)"
            }
            
            val response = apiService.searchRecording(query = query, limit = 5)
            val recordings = response.recordings ?: return@withContext null
            
            // Flatten all releases from the top recordings
            val allReleases = recordings.flatMap { it.releases ?: emptyList() }
            
            // Sort releases by prioritizing exact title matches first, then primary type
            val sortedReleases = allReleases.sortedBy { release ->
                var score = 0
                
                // Massive boost if the release title matches the track title (it's the Single!)
                if (release.title.equals(trackName, ignoreCase = true)) {
                    score -= 10
                }
                
                when (release.releaseGroup?.primaryType?.lowercase()) {
                    "single" -> score -= 2
                    "album" -> score -= 1
                }
                score
            }
            
            var checkCount = 0
            for (release in sortedReleases) {
                if (checkCount >= 5) return@withContext null // limit to 5 HTTP checks
                
                val url = "https://coverartarchive.org/release/${release.id}/front-250.jpg"
                try {
                    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "HEAD"
                    connection.connectTimeout = 1500
                    connection.readTimeout = 1500
                    
                    val code = connection.responseCode
                    if (code == 200 || code == 307 || code == 302 || code == 301) {
                        return@withContext url
                    }
                } catch (e: Exception) {
                    // Ignore and try the next release
                } finally {
                    checkCount++
                }
            }
            
            null
        } catch (e: Exception) {
            null
        }
    }
}
