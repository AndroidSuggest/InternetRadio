package com.armanmaurya.internetradio.recording.engine

import com.armanmaurya.internetradio.core.system.FileSystemFacade
import com.armanmaurya.internetradio.core.utils.AudioFormatUtils
import com.armanmaurya.internetradio.recording.engine.recorder.DirectStreamRecorder
import com.armanmaurya.internetradio.recording.engine.recorder.HlsStreamRecorder
import com.armanmaurya.internetradio.recording.engine.recorder.StreamRecorder
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingEngine @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val fileSystemFacade: FileSystemFacade
) {
    suspend fun record(
        url: String,
        title: String,
        onBytesWritten: (Long) -> Unit
    ) {
        val sink = RecordingSink(fileSystemFacade, title)
        val recorder: StreamRecorder = if (isHls(url)) {
            HlsStreamRecorder(okHttpClient)
        } else {
            DirectStreamRecorder(okHttpClient)
        }

        try {
            recorder.record(
                url = url,
                sink = sink,
                onBytesWritten = onBytesWritten
            )
        } finally {
            sink.close()
        }
    }

    private fun isHls(url: String): Boolean {
        if (url.contains(".m3u8", ignoreCase = true)) return true
        return try {
            val req = Request.Builder().url(url).head().build()
            val resp = okHttpClient.newCall(req).execute()
            val ct = resp.header("Content-Type", "") ?: ""
            resp.close()
            AudioFormatUtils.isHlsContentType(ct)
        } catch (_: Exception) {
            false
        }
    }
}
