package com.armanmaurya.internetradio.core.utils

object TrackSanitizer {
    fun sanitizeTrackInfo(info: String): String {
        return info
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
