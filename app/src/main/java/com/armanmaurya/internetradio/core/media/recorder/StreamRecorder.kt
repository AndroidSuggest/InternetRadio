package com.armanmaurya.internetradio.core.media.recorder

import com.armanmaurya.internetradio.core.media.recorder.backend.DirectStreamRecorder
import com.armanmaurya.internetradio.core.media.recorder.backend.HlsStreamRecorder
import com.armanmaurya.internetradio.core.system.FileSystemFacade
import com.armanmaurya.internetradio.core.utils.AudioFormatUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

interface StreamRecorder {
    suspend fun record(
        config: RecordingConfig,
        onBytesWritten: (bytes: Long) -> Unit
    )
}

@Singleton
class DefaultStreamRecorder @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val fileSystemFacade: FileSystemFacade
) : StreamRecorder {

    override suspend fun record(
        config: RecordingConfig,
        onBytesWritten: (bytes: Long) -> Unit
    ) {
        val sink = RecordingSink(fileSystemFacade, config.title)
        try {
            if (isHls(config.url)) {
                HlsStreamRecorder(okHttpClient).record(config, sink, onBytesWritten)
            } else {
                DirectStreamRecorder(okHttpClient).record(config, sink, onBytesWritten)
            }
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
