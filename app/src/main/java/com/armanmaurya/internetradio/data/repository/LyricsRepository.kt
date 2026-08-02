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
            fun cleanQuery(query: String): String {
                return query
                    // 1. Completely remove anything inside (), [], or {}
                    .replace(Regex("\\(.*?\\)|\\[.*?\\]|\\{.*?\\}"), "")
                    // 2. Remove common metadata words
                    .replace(Regex("(?i)\\b(and|feat\\.?|ft\\.?)\\b"), "")
                    // 3. Remove hyphens and extra punctuation
                    .replace(Regex("[&,\\-]"), " ")
                    // 4. Collapse multiple spaces into a single space
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }

            suspend fun searchAndFindBestMatch(query: String): com.armanmaurya.internetradio.data.remote.dto.LrcLibResponse? {
                val responses = lrcLibApi.searchLyrics(query)
                val valid = responses.filter { !it.instrumental && (!it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank()) }
                // Prefer synced lyrics over plain lyrics
                return valid.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: valid.firstOrNull()
            }

            // 1. Cleaned full query without hyphens or extra punctuation
            val cleanedFull = cleanQuery(trackName)
            var bestMatch = if (cleanedFull.isNotBlank()) searchAndFindBestMatch(cleanedFull) else null
            
            // 2. Just the song title (cleaned)
            if (bestMatch == null && trackName.contains(" - ")) {
                val parts = trackName.split(" - ", limit = 2)
                if (parts.size == 2) {
                    val cleanedTitle = cleanQuery(parts[0]) // Title is now first
                    if (cleanedTitle.isNotBlank()) {
                        bestMatch = searchAndFindBestMatch(cleanedTitle)
                    }
                }
            }

            if (bestMatch != null) {
                val parsedSyncedLyrics = bestMatch.syncedLyrics?.let { parseLrc(it) }
                emit(LyricsState.Success(bestMatch.plainLyrics, parsedSyncedLyrics))
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
