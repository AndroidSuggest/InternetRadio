package com.armanmaurya.internetradio.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.armanmaurya.internetradio.data.model.RadioStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class RecordingSession(
    val station: RadioStation,
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val scope: CoroutineScope,
    private val fileSystemFacade: com.armanmaurya.internetradio.core.system.FileSystemFacade,
    private val onStopped: (uuid: String, bytes: Long) -> Unit
) {
    private val _durationSeconds = MutableStateFlow(0L)
    val durationSeconds: StateFlow<Long> = _durationSeconds.asStateFlow()
    var bytesWritten = 0L
    val startTimeMs = System.currentTimeMillis()
    private var job: Job? = null

    fun start() {
        job = scope.launch(Dispatchers.IO) {
            launch { while (isActive) { delay(1000); _durationSeconds.update { it + 1 } } }

            try {
                val request = Request.Builder()
                    .url(station.urlResolved)
                    .header("Icy-MetaData", "0")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val contentType = response.header("Content-Type", "") ?: ""

                if (StreamFormatUtils.isHlsContentType(contentType)) {
                    var playlistContent = response.body?.string() ?: ""
                    response.close()

                    var playlistUrl = station.urlResolved

                    if (HlsPlaylistParser.isMasterPlaylist(playlistContent)) {
                        val variantUrl = HlsPlaylistParser.getHighestQualityVariantUrl(playlistContent, playlistUrl)
                        if (variantUrl != null) {
                            playlistUrl = variantUrl
                            playlistContent = fetchText(playlistUrl) ?: ""
                        }
                    }

                    handleHlsStream(playlistContent, playlistUrl)
                } else {
                    handleDirectStream(response)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) e.printStackTrace()
            } finally {
                onStopped(station.stationUuid, bytesWritten)
            }
        }
    }

    fun stop() { job?.cancel() }

    private suspend fun handleDirectStream(response: Response) {
        val rawBodyStream = response.body?.byteStream() ?: run {
            response.close()
            return
        }
        
        val bodyStream = java.io.PushbackInputStream(rawBodyStream, 4)
        val magic = ByteArray(4)
        val magicRead = bodyStream.read(magic, 0, 4)
        if (magicRead > 0) {
            bodyStream.unread(magic, 0, magicRead)
        }
        
        val format = StreamFormatUtils.audioFormatFromMagicBytes(magic)
        val sink = openSink(format.extension) ?: return
        
        try {
            if (format == StreamFormatUtils.AudioFormat.OGG) {
                OggRewriter.remuxStream(bodyStream, sink.outputStream, scope, job) { written ->
                    bytesWritten += written
                }
            } else {
                val buf = ByteArray(8192)
                while (scope.isActive && job?.isActive == true) {
                    val len = bodyStream.read(buf)
                    if (len == -1) break
                    sink.outputStream.write(buf, 0, len)
                    bytesWritten += len
                }
            }
        } finally {
            sink.close()
            response.close()
        }
    }

    private suspend fun handleHlsStream(initialPlaylistText: String, playlistUrl: String) {
        var lastSequence = -1L
        var playlistText = initialPlaylistText
        
        val initialPlaylist = HlsPlaylistParser.parse(playlistText, playlistUrl)
        val ext = if (initialPlaylist.segments.firstOrNull()?.url?.contains(".aac") == true) "aac" else "ts"
        
        val sink = openSink(ext) ?: return
        
        try {
            while (scope.isActive && job?.isActive == true) {
                val playlist = HlsPlaylistParser.parse(playlistText, playlistUrl)
                
                val newSegs = if (lastSequence < 0) {
                    playlist.segments.takeLast(1)
                } else {
                    playlist.segments.filter { it.mediaSequence > lastSequence }
                }

                for (seg in newSegs) {
                    if (!(scope.isActive && job?.isActive == true)) break
                    val bytes = downloadSegment(seg.url) ?: continue
                    sink.outputStream.write(bytes)
                    bytesWritten += bytes.size
                    lastSequence = seg.mediaSequence
                }

                if (!(scope.isActive && job?.isActive == true)) break
                delay((playlist.targetDurationSeconds * 500L).coerceAtLeast(2000L))
                playlistText = fetchText(playlistUrl) ?: break
            }
        } finally {
            sink.close()
        }
    }
    
    private fun downloadSegment(url: String): ByteArray? {
        return try {
            val req = Request.Builder().url(url).build()
            okHttpClient.newCall(req).execute().use { it.body?.bytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun fetchText(url: String): String? {
        return try {
            val req = Request.Builder().url(url).build()
            okHttpClient.newCall(req).execute().use { it.body?.string() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    class OutputSink(
        val outputStream: java.io.OutputStream, 
        private val pfd: android.os.ParcelFileDescriptor?,
        private val finalizeAction: (() -> Unit)? = null
    ) : java.io.Closeable {
        override fun close() {
            try { outputStream.close() } catch (e: Exception) {}
            try { pfd?.close() } catch (e: Exception) {}
            try { finalizeAction?.invoke() } catch (e: Exception) {}
        }
    }

    private suspend fun openSink(ext: String): OutputSink? {
        val safeStationName = station.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val timestamp = java.text.SimpleDateFormat("d MMMM yyyy hh-mm a", java.util.Locale.getDefault()).format(java.util.Date())
        val fileName = "$safeStationName $timestamp.$ext"
        val partFileName = "$fileName.part"
        val folderName = "InternetRadio/$safeStationName"
        val mimeType = when (ext) {
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            else  -> "audio/mpeg"
        }

        val (uri, pfd) = fileSystemFacade.createAudioRecordingFile(folderName, partFileName, mimeType) ?: return null
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        
        return OutputSink(outputStream, pfd) {
            kotlinx.coroutines.runBlocking {
                try { fileSystemFacade.finalizeAudioRecordingFile(uri, fileName) } catch (e: Exception) {}
            }
        }
    }
}
