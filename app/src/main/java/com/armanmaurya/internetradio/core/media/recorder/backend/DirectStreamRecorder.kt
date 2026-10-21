package com.armanmaurya.internetradio.core.media.recorder.backend

import com.armanmaurya.internetradio.core.media.recorder.RecordingConfig
import com.armanmaurya.internetradio.core.media.recorder.RecordingSink
import com.armanmaurya.internetradio.core.media.recorder.RecorderException
import com.armanmaurya.internetradio.core.utils.AudioFormat
import com.armanmaurya.internetradio.core.utils.AudioFormatUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DirectStreamRecorder(
    private val okHttpClient: OkHttpClient
) {

    suspend fun record(
        config: RecordingConfig,
        sink: RecordingSink,
        onBytesWritten: (bytes: Long) -> Unit,
        onFileOpened: (stationFolderName: String, fileName: String) -> Unit = { _, _ -> },
        onTrackMetadata: (rawTrack: String) -> Unit = {}
    ) {
        val requestBuilder = Request.Builder()
            .url(config.url)
            .header("Icy-MetaData", "1")

        if (!config.userAgent.isNullOrBlank()) {
            requestBuilder.header("User-Agent", config.userAgent)
        }

        val request = requestBuilder.build()
        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            throw RecorderException.StreamConnectionException(config.url, e)
        }

        val rawBodyStream = response.body.byteStream()
        val icyMetaInt = response.header("icy-metaint")?.toIntOrNull() ?: 0
        val demuxedStream: InputStream = if (icyMetaInt > 0) {
            IcyDemuxInputStream(rawBodyStream, icyMetaInt, onTrackMetadata)
        } else {
            rawBodyStream
        }

        val bodyStream = PushbackInputStream(demuxedStream, 4)
        val magic = ByteArray(4)
        val magicRead = bodyStream.read(magic, 0, 4)
        if (magicRead > 0) {
            bodyStream.unread(magic, 0, magicRead)
        }

        val format = AudioFormatUtils.audioFormatFromMagicBytes(magic)
        val outputStream = sink.open(format.extension) ?: run {
            response.close()
            throw RecorderException.SinkWriteException("Failed to open output sink for extension: ${format.extension}")
        }
        sink.finalFileName?.let { onFileOpened(sink.safeStationName, it) }

        var totalWritten = 0L
        try {
            if (format == AudioFormat.OGG) {
                coroutineScope {
                    OggRewriter.remuxStream(
                        input = bodyStream,
                        output = outputStream,
                        scope = this,
                        job = null,
                        onTrackMetadata = onTrackMetadata
                    ) { written ->
                        totalWritten += written
                        onBytesWritten(written.toLong())
                    }
                }
            } else {
                val buf = ByteArray(config.bufferSizeBytes)
                while (currentCoroutineContext().isActive) {
                    val len = bodyStream.read(buf)
                    if (len == -1) break
                    outputStream.write(buf, 0, len)
                    totalWritten += len
                    onBytesWritten(len.toLong())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive && e !is RecorderException) {
                throw RecorderException.StreamReadException("Stream recording error", e)
            }
        } finally {
            response.close()
            if (totalWritten > 0) {
                sink.finalizeFile()
            }
        }
    }
}

internal object OggRewriter {
    // Standard Ogg CRC32 polynomial: 0x04c11db7
    private val crcTable = IntArray(256)

    init {
        for (i in 0 until 256) {
            var r = i shl 24
            for (j in 0 until 8) {
                r = if ((r and -0x80000000) != 0) {
                    (r shl 1) xor 0x04c11db7
                } else {
                    r shl 1
                }
            }
            crcTable[i] = r
        }
    }

    private fun updateCrc(crc: Int, data: ByteArray, offset: Int, length: Int): Int {
        var currentCrc = crc
        for (i in offset until offset + length) {
            val byteVal = data[i].toInt() and 0xFF
            val index = ((currentCrc ushr 24) xor byteVal) and 0xFF
            currentCrc = (currentCrc shl 8) xor crcTable[index]
        }
        return currentCrc
    }

    fun remuxStream(
        input: InputStream,
        output: OutputStream,
        scope: CoroutineScope,
        job: Job?,
        onTrackMetadata: (String) -> Unit = {},
        onBytesWritten: (Int) -> Unit
    ) {
        var firstGranulePosition = -1L

        val headerBuf = ByteArray(27)
        val segmentTable = ByteArray(255)
        var pageData = ByteArray(65536)

        while (scope.isActive && (job == null || job.isActive)) {
            // Find OggS sync
            var synced = false
            while (scope.isActive && (job == null || job.isActive)) {
                val b = input.read()
                if (b == -1) return
                if (b == 0x4F) { // 'O'
                    headerBuf[0] = b.toByte()
                    if (readFully(input, headerBuf, 3, 1)) {
                        if (headerBuf[1] == 0x67.toByte() && headerBuf[2] == 0x67.toByte() && headerBuf[3] == 0x53.toByte()) {
                            synced = true
                            break
                        }
                    }
                }
            }

            if (!synced) break

            // Read the rest of the 27-byte header
            if (!readFully(input, headerBuf, 23, 4)) break

            // Read Granule Position (bytes 6-13, little endian)
            val buffer = ByteBuffer.wrap(headerBuf, 6, 8).order(ByteOrder.LITTLE_ENDIAN)
            val originalGranule = buffer.long

            // Rewrite Granule Position
            var newGranule = originalGranule
            if (originalGranule != -1L && originalGranule != 0L) {
                if (firstGranulePosition == -1L) {
                    firstGranulePosition = originalGranule
                }
                newGranule = originalGranule - firstGranulePosition
                if (newGranule < 0) newGranule = 0
            }

            val newBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            newBuffer.putLong(newGranule)
            val newGranuleBytes = newBuffer.array()
            System.arraycopy(newGranuleBytes, 0, headerBuf, 6, 8)

            // Clear the CRC field (bytes 22-25) before calculating
            headerBuf[22] = 0
            headerBuf[23] = 0
            headerBuf[24] = 0
            headerBuf[25] = 0

            // Read segment count
            val segmentCount = headerBuf[26].toInt() and 0xFF

            // Read segment table
            if (segmentCount > 0) {
                if (!readFully(input, segmentTable, segmentCount, 0)) break
            }

            var dataSize = 0
            for (i in 0 until segmentCount) {
                dataSize += segmentTable[i].toInt() and 0xFF
            }

            // Resize pageData buffer if needed
            if (pageData.size < dataSize) {
                pageData = ByteArray(dataSize)
            }

            // Read page data
            if (dataSize > 0) {
                if (!readFully(input, pageData, dataSize, 0)) break
                extractOggTrackTitle(pageData, dataSize)?.let(onTrackMetadata)
            }

            // Calculate CRC
            var crc = 0
            crc = updateCrc(crc, headerBuf, 0, 27)
            if (segmentCount > 0) {
                crc = updateCrc(crc, segmentTable, 0, segmentCount)
            }
            if (dataSize > 0) {
                crc = updateCrc(crc, pageData, 0, dataSize)
            }

            // Write CRC (little endian)
            headerBuf[22] = (crc and 0xFF).toByte()
            headerBuf[23] = ((crc ushr 8) and 0xFF).toByte()
            headerBuf[24] = ((crc ushr 16) and 0xFF).toByte()
            headerBuf[25] = ((crc ushr 24) and 0xFF).toByte()

            // Write out the modified page
            output.write(headerBuf, 0, 27)
            if (segmentCount > 0) {
                output.write(segmentTable, 0, segmentCount)
            }
            if (dataSize > 0) {
                output.write(pageData, 0, dataSize)
            }

            onBytesWritten(27 + segmentCount + dataSize)
        }
    }

    internal fun extractOggTrackTitle(data: ByteArray, length: Int): String? {
        if (length < 16) return null
        val commentOffset = when {
            length >= 15 &&
                data[0] == 0x03.toByte() &&
                data[1] == 'v'.code.toByte() &&
                data[2] == 'o'.code.toByte() &&
                data[3] == 'r'.code.toByte() &&
                data[4] == 'b'.code.toByte() &&
                data[5] == 'i'.code.toByte() &&
                data[6] == 's'.code.toByte() -> 7
            length >= 16 &&
                data[0] == 'O'.code.toByte() &&
                data[1] == 'p'.code.toByte() &&
                data[2] == 'u'.code.toByte() &&
                data[3] == 's'.code.toByte() &&
                data[4] == 'T'.code.toByte() &&
                data[5] == 'a'.code.toByte() &&
                data[6] == 'g'.code.toByte() &&
                data[7] == 's'.code.toByte() -> 8
            else -> return null
        }

        return try {
            val buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(commentOffset)
            if (buf.remaining() < 4) return null
            val vendorLen = buf.int
            if (vendorLen < 0 || buf.remaining() < vendorLen + 4) return null
            buf.position(buf.position() + vendorLen)

            val commentCount = buf.int
            if (commentCount <= 0 || commentCount > 256) return null

            var title: String? = null
            var artist: String? = null

            for (i in 0 until commentCount) {
                if (buf.remaining() < 4) break
                val cLen = buf.int
                if (cLen < 0 || buf.remaining() < cLen) break
                val comment = String(data, buf.position(), cLen, Charsets.UTF_8)
                buf.position(buf.position() + cLen)

                when {
                    comment.startsWith("TITLE=", ignoreCase = true) -> {
                        title = comment.substring(6).trim().takeIf { it.isNotBlank() }
                    }
                    comment.startsWith("ARTIST=", ignoreCase = true) -> {
                        artist = comment.substring(7).trim().takeIf { it.isNotBlank() }
                    }
                }
            }

            when {
                !artist.isNullOrBlank() && !title.isNullOrBlank() -> "$artist - $title"
                !title.isNullOrBlank() -> title
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int, offset: Int): Boolean {
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(buffer, offset + totalRead, length - totalRead)
            if (read == -1) return false
            totalRead += read
        }
        return true
    }
}
