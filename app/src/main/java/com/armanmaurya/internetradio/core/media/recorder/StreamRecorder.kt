package com.armanmaurya.internetradio.core.media.recorder

import android.content.Context
import com.armanmaurya.internetradio.core.media.recorder.backend.DirectStreamRecorder
import com.armanmaurya.internetradio.core.media.recorder.backend.HlsStreamRecorder
import com.armanmaurya.internetradio.core.system.FileSystemFacade
import com.armanmaurya.internetradio.core.utils.AudioFormatUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
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
    private val fileSystemFacade: FileSystemFacade,
    @ApplicationContext private val context: Context
) : StreamRecorder {

    private val recordingHttpClient = okHttpClient.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun record(
        config: RecordingConfig,
        onBytesWritten: (bytes: Long) -> Unit
    ) {
        val sink = RecordingSink(fileSystemFacade, config.title, context)
        try {
            if (isHls(config.url)) {
                HlsStreamRecorder(recordingHttpClient).record(config, sink, onBytesWritten)
            } else {
                DirectStreamRecorder(recordingHttpClient).record(config, sink, onBytesWritten)
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
