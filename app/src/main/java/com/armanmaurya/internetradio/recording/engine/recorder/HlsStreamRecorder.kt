package com.armanmaurya.internetradio.recording.engine.recorder

import com.armanmaurya.internetradio.recording.engine.RecordingSink
import com.armanmaurya.internetradio.recording.engine.format.HlsPlaylistParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request

class HlsStreamRecorder(
    private val okHttpClient: OkHttpClient
) : StreamRecorder {

    override suspend fun record(
        url: String,
        sink: RecordingSink,
        onBytesWritten: (bytes: Long) -> Unit
    ) {
        var playlistUrl = url
        var playlistContent = fetchText(playlistUrl) ?: return

        if (HlsPlaylistParser.isMasterPlaylist(playlistContent)) {
            val variantUrl = HlsPlaylistParser.getHighestQualityVariantUrl(playlistContent, playlistUrl)
            if (variantUrl != null) {
                playlistUrl = variantUrl
                playlistContent = fetchText(playlistUrl) ?: return
            }
        }

        var lastSequence = -1L
        val initialPlaylist = HlsPlaylistParser.parse(playlistContent, playlistUrl)
        val ext = if (initialPlaylist.segments.firstOrNull()?.url?.contains(".aac") == true) "aac" else "ts"

        val outputStream = sink.open(ext) ?: return
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
                    val bytes = downloadSegment(seg.url) ?: continue
                    outputStream.write(bytes)
                    totalWritten += bytes.size
                    onBytesWritten(bytes.size.toLong())
                    lastSequence = seg.mediaSequence
                }

                if (!currentCoroutineContext().isActive) break
                delay((playlist.targetDurationSeconds * 500L).coerceAtLeast(2000L))
                currentPlaylistText = fetchText(playlistUrl) ?: break
            }
        } finally {
            if (totalWritten > 0) {
                sink.finalizeFile()
            }
        }
    }

    private fun downloadSegment(url: String): ByteArray? {
        return try {
            val req = Request.Builder().url(url).build()
            okHttpClient.newCall(req).execute().use { it.body?.bytes() }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchText(url: String): String? {
        return try {
            val req = Request.Builder().url(url).build()
            okHttpClient.newCall(req).execute().use { it.body?.string() }
        } catch (_: Exception) {
            null
        }
    }
}
