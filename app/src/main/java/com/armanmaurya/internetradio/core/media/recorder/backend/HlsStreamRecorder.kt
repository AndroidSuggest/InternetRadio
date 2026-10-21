package com.armanmaurya.internetradio.core.media.recorder.backend

import com.armanmaurya.internetradio.core.media.recorder.RecordingConfig
import com.armanmaurya.internetradio.core.media.recorder.RecordingSink
import com.armanmaurya.internetradio.core.media.recorder.RecorderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

class HlsStreamRecorder(
    private val okHttpClient: OkHttpClient
) {

    suspend fun record(
        config: RecordingConfig,
        sink: RecordingSink,
        onBytesWritten: (bytes: Long) -> Unit,
        onFileOpened: (stationFolderName: String, fileName: String) -> Unit = { _, _ -> },
        onTrackMetadata: (rawTrack: String, audioOffsetMs: Long) -> Unit = { _, _ -> },
        onAudioProgress: (audioDurationMs: Long) -> Unit = {},
        onReconnectingStateChanged: (Boolean) -> Unit = {}
    ) {
        var playlistUrl = config.url
        var playlistContent = fetchText(playlistUrl, config.userAgent) ?: run {
            throw RecorderException.StreamConnectionException(config.url, Exception("Failed to fetch HLS playlist"))
        }

        if (HlsPlaylistParser.isMasterPlaylist(playlistContent)) {
            val variantUrl = HlsPlaylistParser.getHighestQualityVariantUrl(playlistContent, playlistUrl)
            if (variantUrl != null) {
                playlistUrl = variantUrl
                playlistContent = fetchText(playlistUrl, config.userAgent) ?: run {
                    throw RecorderException.StreamConnectionException(variantUrl, Exception("Failed to fetch HLS variant playlist"))
                }
            }
        }

        var lastSequence = -1L
        val initialPlaylist = try {
            HlsPlaylistParser.parse(playlistContent, playlistUrl)
        } catch (e: Exception) {
            throw RecorderException.PlaylistParseException("Failed to parse initial HLS playlist", e)
        }

        val ext = if (initialPlaylist.segments.firstOrNull()?.url?.contains(".aac") == true) "aac" else "ts"
        val outputStream = sink.open(ext) ?: run {
            throw RecorderException.SinkWriteException("Failed to open output sink for HLS extension: $ext")
        }
        sink.finalFileName?.let { onFileOpened(sink.safeStationName, it) }

        var totalWritten = 0L
        var accumulatedDurationMs = 0L
        var isReconnecting = false

        try {
            var currentPlaylistText = playlistContent
            while (currentCoroutineContext().isActive) {
                val playlist = HlsPlaylistParser.parse(currentPlaylistText, playlistUrl)

                val newSegs = if (lastSequence < 0) {
                    playlist.segments.takeLast(1)
                } else {
                    playlist.segments.filter { it.mediaSequence > lastSequence }
                }

                for (seg in newSegs) {
                    if (!currentCoroutineContext().isActive) break
                    val bytes = downloadSegment(seg.url, config.userAgent) ?: continue
                    val segDurationMs = (seg.durationSeconds * 1000.0).toLong()
                    val trackTitle = HlsPlaylistParser.extractSegmentMetadata(bytes) ?: seg.title
                    if (!trackTitle.isNullOrBlank()) {
                        onTrackMetadata(trackTitle, accumulatedDurationMs)
                    }
                    outputStream.write(bytes)
                    totalWritten += bytes.size
                    accumulatedDurationMs += segDurationMs
                    onAudioProgress(accumulatedDurationMs)
                    onBytesWritten(bytes.size.toLong())
                    lastSequence = seg.mediaSequence
                }

                if (!currentCoroutineContext().isActive) break
                delay((playlist.targetDurationSeconds * 500L).coerceAtLeast(2000L))

                var nextText = fetchText(playlistUrl, config.userAgent)
                if (nextText == null) {
                    if (!isReconnecting) {
                        isReconnecting = true
                        onReconnectingStateChanged(true)
                    }
                    val reconnectedText = attemptHlsReconnect(playlistUrl, config)
                    if (reconnectedText == null) {
                        break
                    }
                    nextText = reconnectedText
                }

                if (isReconnecting) {
                    isReconnecting = false
                    onReconnectingStateChanged(false)
                }
                currentPlaylistText = nextText
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (currentCoroutineContext().isActive && e !is RecorderException) {
                throw RecorderException.StreamReadException("HLS segment recording error", e)
            }
        } finally {
            if (isReconnecting) {
                onReconnectingStateChanged(false)
            }
            if (totalWritten > 0) {
                sink.finalizeFile()
            }
        }
    }

    private suspend fun attemptHlsReconnect(
        playlistUrl: String,
        config: RecordingConfig
    ): String? {
        val startTime = System.currentTimeMillis()
        var currentDelay = config.minRetryDelayMs

        while (currentCoroutineContext().isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            val maxRetry = config.getMaxRetryDuration()
            if (maxRetry >= 0 && elapsed >= maxRetry) {
                return null
            }

            delay(currentDelay)
            if (!currentCoroutineContext().isActive) return null

            val text = fetchText(playlistUrl, config.userAgent)
            if (text != null) {
                return text
            }

            currentDelay = (currentDelay * 2).coerceAtMost(config.maxRetryDelayMs)
        }
        return null
    }

    private fun downloadSegment(url: String, userAgent: String?): ByteArray? {
        return try {
            val reqBuilder = Request.Builder().url(url)
            if (!userAgent.isNullOrBlank()) {
                reqBuilder.header("User-Agent", userAgent)
            }
            okHttpClient.newCall(reqBuilder.build()).execute().use { it.body.bytes() }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchText(url: String, userAgent: String?): String? {
        return try {
            val reqBuilder = Request.Builder().url(url)
            if (!userAgent.isNullOrBlank()) {
                reqBuilder.header("User-Agent", userAgent)
            }
            okHttpClient.newCall(reqBuilder.build()).execute().use { it.body.string() }
        } catch (_: Exception) {
            null
        }
    }
}

internal object HlsPlaylistParser {
    data class HlsSegment(
        val url: String,
        val mediaSequence: Long,
        val durationSeconds: Double = 10.0,
        val title: String? = null
    )
    data class HlsPlaylist(val targetDurationSeconds: Int, val segments: List<HlsSegment>)

    fun parse(content: String, baseUrl: String): HlsPlaylist {
        val lines = content.lines()
        var targetDuration = 10
        var baseSeq = 0L
        var seqOffset = 0L
        val segments = mutableListOf<HlsSegment>()
        var expectSegment = false
        var pendingDuration = 10.0
        var pendingTitle: String? = null

        for (line in lines) {
            val t = line.trim()
            when {
                t.startsWith("#EXT-X-TARGETDURATION:") ->
                    targetDuration = t.substringAfter(":").toIntOrNull() ?: 10
                t.startsWith("#EXT-X-MEDIA-SEQUENCE:") ->
                    baseSeq = t.substringAfter(":").toLongOrNull() ?: 0L
                t.startsWith("#EXTINF:") -> {
                    expectSegment = true
                    val afterColon = t.substringAfter(":")
                    val durStr = afterColon.substringBefore(",").trim()
                    pendingDuration = durStr.toDoubleOrNull() ?: targetDuration.toDouble()
                    val rawTitle = if (afterColon.contains(",")) {
                        afterColon.substringAfter(",").trim()
                    } else ""
                    pendingTitle = parseExtInfTitle(rawTitle)
                }
                !t.startsWith("#") && t.isNotBlank() && expectSegment -> {
                    val absUrl = resolveUrl(t, baseUrl)
                    segments.add(HlsSegment(absUrl, baseSeq + seqOffset, pendingDuration, pendingTitle))
                    seqOffset++
                    expectSegment = false
                    pendingTitle = null
                    pendingDuration = targetDuration.toDouble()
                }
            }
        }
        return HlsPlaylist(targetDuration, segments)
    }

    private fun parseExtInfTitle(raw: String): String? {
        val cleaned = raw.trim().trim('"', '\'')
        if (cleaned.isBlank()) return null
        val lower = cleaned.lowercase()
        if (lower == "no desc" || lower == "live" || lower == "null" || lower == "none") return null
        if (cleaned.all { it.isDigit() || it == '.' || it == '-' || it == '_' }) return null
        // Handle key-value style like title="Song",artist="Artist"
        if (cleaned.contains("title=", ignoreCase = true)) {
            val titleMatch = Regex("""(?i)title="([^"]*)"""").find(cleaned)?.groupValues?.get(1)?.trim()
            val artistMatch = Regex("""(?i)artist="([^"]*)"""").find(cleaned)?.groupValues?.get(1)?.trim()
            return when {
                !artistMatch.isNullOrBlank() && !titleMatch.isNullOrBlank() -> "$artistMatch - $titleMatch"
                !titleMatch.isNullOrBlank() -> titleMatch
                else -> null
            }
        }
        return cleaned
    }

    fun extractSegmentMetadata(bytes: ByteArray): String? {
        if (bytes.size < 10) return null
        // Check for ID3v2 header at start of segment or embedded inside MPEG-TS
        val id3Offset = findId3Offset(bytes)
        if (id3Offset < 0) return null

        val slice = if (id3Offset == 0) bytes else bytes.copyOfRange(id3Offset, (id3Offset + 2048).coerceAtMost(bytes.size))
        val (_, parsed) = com.armanmaurya.internetradio.core.media.prober.parser.AudioFrameParser.parseId3v2(slice)
        return parsed.title?.takeIf { it.isNotBlank() }
    }

    private fun findId3Offset(bytes: ByteArray): Int {
        val limit = (bytes.size - 10).coerceAtMost(4096)
        for (i in 0..limit) {
            if (bytes[i] == 'I'.code.toByte() &&
                bytes[i + 1] == 'D'.code.toByte() &&
                bytes[i + 2] == '3'.code.toByte() &&
                (bytes[i + 3].toInt() and 0xFF) in 2..4
            ) {
                return i
            }
        }
        return -1
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
