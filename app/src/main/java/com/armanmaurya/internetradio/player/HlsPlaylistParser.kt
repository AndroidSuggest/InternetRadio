package com.armanmaurya.internetradio.player

import java.net.URI

object HlsPlaylistParser {
    data class HlsSegment(val url: String, val mediaSequence: Long)
    data class HlsPlaylist(val targetDurationSeconds: Int, val segments: List<HlsSegment>)

    fun parse(content: String, baseUrl: String): HlsPlaylist {
        val lines = content.lines()
        var targetDuration = 10
        var baseSeq = 0L
        var seqOffset = 0L
        val segments = mutableListOf<HlsSegment>()
        var expectSegment = false

        for (line in lines) {
            val t = line.trim()
            when {
                t.startsWith("#EXT-X-TARGETDURATION:") ->
                    targetDuration = t.substringAfter(":").toIntOrNull() ?: 10
                t.startsWith("#EXT-X-MEDIA-SEQUENCE:") ->
                    baseSeq = t.substringAfter(":").toLongOrNull() ?: 0L
                t.startsWith("#EXTINF:") ->
                    expectSegment = true
                !t.startsWith("#") && t.isNotBlank() && expectSegment -> {
                    val absUrl = resolveUrl(t, baseUrl)
                    segments.add(HlsSegment(absUrl, baseSeq + seqOffset))
                    seqOffset++
                    expectSegment = false
                }
            }
        }
        return HlsPlaylist(targetDuration, segments)
    }

    fun isMasterPlaylist(content: String): Boolean {
        return content.contains("#EXT-X-STREAM-INF")
    }

    fun getHighestQualityVariantUrl(content: String, baseUrl: String): String? {
        val lines = content.lines()
        var bestUrl: String? = null
        var maxBandwidth = -1
        var currentBandwidth = 0
        
        for (line in lines) {
            val t = line.trim()
            if (t.startsWith("#EXT-X-STREAM-INF")) {
                val bwMatch = Regex("BANDWIDTH=(\\d+)").find(t)
                currentBandwidth = bwMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            } else if (!t.startsWith("#") && t.isNotBlank() && currentBandwidth >= 0) {
                if (currentBandwidth >= maxBandwidth) {
                    maxBandwidth = currentBandwidth
                    bestUrl = resolveUrl(t, baseUrl)
                }
                currentBandwidth = -1 // Reset after consuming the URI
            }
        }
        return bestUrl
    }

    private fun resolveUrl(path: String, base: String): String {
        return try {
            URI(base).resolve(path).toString()
        } catch (e: Exception) {
            if (path.startsWith("http")) path else {
                val baseDir = base.substringBeforeLast("/")
                "$baseDir/$path"
            }
        }
    }
}
