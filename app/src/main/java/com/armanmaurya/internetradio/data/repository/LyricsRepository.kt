package com.armanmaurya.internetradio.data.repository

import com.armanmaurya.internetradio.data.model.LrcLine
import com.armanmaurya.internetradio.data.model.LyricsState
import com.armanmaurya.internetradio.data.remote.LrcLibApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRepository @Inject constructor(
    private val lrcLibApi: LrcLibApi
) {
    fun getLyricsForTrack(trackName: String): Flow<LyricsState> = flow {
        emit(LyricsState.Loading)
        try {
            var response = lrcLibApi.searchLyrics(trackName)

            // Helper function to clean common words and punctuation
            fun cleanQuery(query: String): String {
                return query
                    .replace(Regex("(?i)\\b(and|feat\\.?|ft\\.?)\\b"), "")
                    .replace(Regex("[&,\\(\\)\\[\\]\\-]"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }

            // Fallback 1: Cleaned full query without hyphens or extra punctuation
            if (response.isEmpty()) {
                val cleaned = cleanQuery(trackName)
                if (cleaned != trackName && cleaned.isNotBlank()) {
                    response = lrcLibApi.searchLyrics(cleaned)
                }
            }
            
            // Fallback 2: Just the song title (cleaned)
            if (response.isEmpty() && trackName.contains(" - ")) {
                val parts = trackName.split(" - ", limit = 2)
                if (parts.size == 2) {
                    val cleanedTitle = cleanQuery(parts[1])
                    if (cleanedTitle.isNotBlank()) {
                        response = lrcLibApi.searchLyrics(cleanedTitle)
                    }
                }
            }

            if (response.isNotEmpty()) {
                val firstMatch = response.first()
                val parsedSyncedLyrics = firstMatch.syncedLyrics?.let { parseLrc(it) }
                emit(LyricsState.Success(firstMatch.plainLyrics, parsedSyncedLyrics))
            } else {
                emit(LyricsState.NotAvailable)
            }
        } catch (e: Exception) {
            emit(LyricsState.NotAvailable)
        }
    }

    private fun parseLrc(lrcContent: String): List<LrcLine> {
        val lines = lrcContent.lines()
        val result = mutableListOf<LrcLine>()
        // Match [mm:ss.xx] or [mm:ss.xxx]
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        
        for (line in lines) {
            val match = regex.find(line)
            if (match != null) {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val millisPart = match.groupValues[3]
                val millis = if (millisPart.length == 2) millisPart.toLong() * 10 else millisPart.toLong()
                
                val text = match.groupValues[4].trim()
                val totalMillis = (minutes * 60 + seconds) * 1000 + millis
                result.add(LrcLine(totalMillis, text))
            }
        }
        return result
    }
}
