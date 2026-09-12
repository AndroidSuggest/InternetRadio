package com.armanmaurya.internetradio.recording.engine.recorder

import com.armanmaurya.internetradio.core.utils.AudioFormat
import com.armanmaurya.internetradio.core.utils.AudioFormatUtils
import com.armanmaurya.internetradio.recording.engine.RecordingSink
import com.armanmaurya.internetradio.recording.engine.format.OggRewriter
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.PushbackInputStream

class DirectStreamRecorder(
    private val okHttpClient: OkHttpClient
) : StreamRecorder {

    override suspend fun record(
        url: String,
        sink: RecordingSink,
        onBytesWritten: (bytes: Long) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Icy-MetaData", "0")
            .build()

        val response = okHttpClient.newCall(request).execute()
        val rawBodyStream = response.body?.byteStream() ?: run {
            response.close()
            return
        }

        val bodyStream = PushbackInputStream(rawBodyStream, 4)
        val magic = ByteArray(4)
        val magicRead = bodyStream.read(magic, 0, 4)
        if (magicRead > 0) {
            bodyStream.unread(magic, 0, magicRead)
        }

        val format = AudioFormatUtils.audioFormatFromMagicBytes(magic)
        val outputStream = sink.open(format.extension) ?: run {
            response.close()
            return
        }

        var totalWritten = 0L
        try {
            if (format == AudioFormat.OGG) {
                coroutineScope {
                    OggRewriter.remuxStream(bodyStream, outputStream, this, null) { written ->
                        totalWritten += written
                        onBytesWritten(written.toLong())
                    }
                }
            } else {
                val buf = ByteArray(8192)
                while (currentCoroutineContext().isActive) {
                    val len = bodyStream.read(buf)
                    if (len == -1) break
                    outputStream.write(buf, 0, len)
                    totalWritten += len
                    onBytesWritten(len.toLong())
                }
            }
        } finally {
            response.close()
            if (totalWritten > 0) {
                sink.finalizeFile()
            }
        }
    }
}
