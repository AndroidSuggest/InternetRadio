package com.armanmaurya.internetradio.core.utils

object TrackSanitizer {

    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val HASH_REGEX = Regex("^[0-9a-fA-F]{16,}$")
    private val NUMERIC_ID_REGEX = Regex("^\\d+$")
    private val SEGMENT_CODE_REGEX = Regex("^(?i)[A-Z]|adv|ad|jingle|promo|sweeper$")
    private val URL_REGEX = Regex("(?i)^(https?://\\S+|www\\.\\S+|[a-z0-9-]+(\\.[a-z0-9-]+)*\\.(com|org|net|fm|radio|tv|co|de|fr|uk|live|stream|audio)(/\\S*)?)$")

    fun isDiscardable(segment: String, isMultiSegmentDelimiter: Boolean = false): Boolean {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return true
        if (UUID_REGEX.matches(trimmed)) return true
        if (HASH_REGEX.matches(trimmed)) return true
        if (URL_REGEX.matches(trimmed)) return true
        if (isMultiSegmentDelimiter) {
            if (NUMERIC_ID_REGEX.matches(trimmed)) return true
            if (SEGMENT_CODE_REGEX.matches(trimmed)) return true
        }
        return false
    }

    /**
     * Cleans raw radio stream metadata by removing unwanted segments such as
     * broadcast delimiters ("||", "|"), station automation IDs, segment codes,
     * UUIDs, hashes, and URLs.
     *
     * Example:
     * "GEORGE MICHAEL - Older || 21930 || S || 2fad6c44-df66-411d-89d9-09ee88efa6bd"
     * -> "GEORGE MICHAEL - Older"
     */
    fun sanitize(raw: String): String {
        var text = raw.trim()
            .replace(" – ", " - ")
            .replace(" — ", " - ")

        // 1. Handle multi-field delimiters like "||" or "|"
        if (text.contains("||") || text.contains("|")) {
            val delimiter = if (text.contains("||")) "||" else "|"
            val valid = text.split(delimiter)
                .map { it.trim() }
                .filter { !isDiscardable(it, isMultiSegmentDelimiter = true) }

            if (valid.isNotEmpty()) {
                text = valid.joinToString(" - ")
            }
        }

        // 2. Filter discardable trailing segments in "Artist - Title - [URL/UUID]"
        if (text.contains(" - ")) {
            val parts = text.split(" - ")
                .map { it.trim() }

            val filtered = parts.filterIndexed { index, part ->
                if (parts.size > 2 && index >= 2 && NUMERIC_ID_REGEX.matches(part)) {
                    false
                } else {
                    !isDiscardable(part, isMultiSegmentDelimiter = false)
                }
            }

            if (filtered.isNotEmpty()) {
                text = filtered.joinToString(" - ")
            }
        }

        return text.trim()
    }

    /**
     * Cleans track information specifically for iTunes / cover art search query.
     */
    fun sanitizeTrackInfo(info: String): String {
        return sanitize(info)
            // 1. Completely remove anything inside (), [], or {}
            .replace(Regex("\\(.*?\\)|\\[.*?\\]|\\{.*?\\}"), "")
            // 2. Remove common metadata words
            .replace(Regex("(?i)\\b(and|feat\\.?|ft\\.?|with)\\b"), " ")
            // 3. Remove common special characters/punctuation often used as separators
            .replace(Regex("[&,\\-\\|/]"), " ")
            // 4. Collapse multiple spaces into a single space
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
