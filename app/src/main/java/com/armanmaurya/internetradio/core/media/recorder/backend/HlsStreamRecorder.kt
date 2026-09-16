package com.armanmaurya.internetradio.core.media.recorder.backend

import com.armanmaurya.internetradio.core.media.recorder.RecordingConfig
import com.armanmaurya.internetradio.core.media.recorder.RecordingSink
import com.armanmaurya.internetradio.core.media.recorder.RecorderException
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
        onBytesWritten: (bytes: Long) -> Unit
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

        var totalWritten = 0L

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
                    outputStream.write(bytes)
                    totalWritten += bytes.size
                    onBytesWritten(bytes.size.toLong())
                    lastSequence = seg.mediaSequence
                }

                if (!currentCoroutineContext().isActive) break
                delay((playlist.targetDurationSeconds * 500L).coerceAtLeast(2000L))
                currentPlaylistText = fetchText(playlistUrl, config.userAgent) ?: break
            }
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive && e !is RecorderException) {
                throw RecorderException.SinkWriteException("HLS segment recording error", e)
            }
        } finally {
            if (totalWritten > 0) {
                sink.finalizeFile()
            }
        }
    }

    private fun downloadSegment(url: String, userAgent: String?): ByteArray? {
        return try {
            val reqBuilder = Request.Builder().url(url)
            if (!userAgent.isNullOrBlank()) {
                reqBuilder.header("User-Agent", userAgent)
            }
            okHttpClient.newCall(reqBuilder.build()).execute().use { it.body?.bytes() }
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
            okHttpClient.newCall(reqBuilder.build()).execute().use { it.body?.string() }
        } catch (_: Exception) {
            null
        }
    }
}

internal object HlsPlaylistParser {
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
