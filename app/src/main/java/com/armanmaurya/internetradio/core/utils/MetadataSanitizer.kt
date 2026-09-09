package com.armanmaurya.internetradio.core.utils

data class ParsedTrackInfo(
    val artist: String?,
    val title: String,
    val combinedDisplay: String
)

object MetadataSanitizer {

    private val UUID_REGEX = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )
    private val HASH_REGEX = Regex(
        "^[0-9a-fA-F]{16,}$"
    )
    private val URL_REGEX = Regex(
        "(?i)^(https?://\\S+|www\\.\\S+|[a-z0-9-]+(\\.[a-z0-9-]+)*\\.(com|org|net|fm|radio|tv|co|de|fr|uk|live|stream|audio)(/\\S*)?)$"
    )

    fun isDiscardableSegment(segment: String): Boolean {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return true
        if (UUID_REGEX.matches(trimmed)) return true
        if (HASH_REGEX.matches(trimmed)) return true
        if (URL_REGEX.matches(trimmed)) return true
        return false
    }

    fun cleanAndParse(rawTrackTitle: String, rawArtist: String? = null): ParsedTrackInfo {
        var text = rawTrackTitle.trim()
            .replace(" – ", " - ")
            .replace(" — ", " - ")

        // 1. Handle multi-field delimiters like || or |
        if (text.contains("||") || text.contains("|")) {
            val delimiter = if (text.contains("||")) "||" else "|"
            val validSegments = text.split(delimiter)
                .map { it.trim() }
                .filter { !isDiscardableSegment(it) }

            if (validSegments.isNotEmpty()) {
                val first = validSegments[0]
                if (first.contains(" - ")) {
                    text = first
                } else if (validSegments.size >= 2) {
                    val artist = rawArtist ?: validSegments[0]
                    val title = validSegments[1]
                    val combined = if (!artist.isNullOrBlank()) "$artist - $title" else title
                    return ParsedTrackInfo(
                        artist = artist,
                        title = title,
                        combinedDisplay = combined
                    )
                } else {
                    text = first
                }
            }
        }

        // 2. Handle hyphen separation (Artist - Title - [URL/UUID])
        if (text.contains(" - ")) {
            val parts = text.split(" - ")
                .map { it.trim() }
                .filter { !isDiscardableSegment(it) }

            if (parts.size >= 2) {
                val artist = rawArtist ?: parts[0]
                val title = if (parts.size == 2) parts[1] else parts.drop(1).joinToString(" - ")
                val combined = if (!artist.isNullOrBlank()) "$artist - $title" else title
                return ParsedTrackInfo(
                    artist = artist,
                    title = title,
                    combinedDisplay = combined
                )
            }
        }

        val artist = rawArtist
        val title = text
        val combined = if (!artist.isNullOrBlank()) "$artist - $title" else title
        return ParsedTrackInfo(
            artist = artist,
            title = title,
            combinedDisplay = combined
        )
    }
}
