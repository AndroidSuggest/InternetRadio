package com.armanmaurya.internetradio.core.media.recorder.backend

import java.io.InputStream

/**
 * Demultiplexes in-band Shoutcast/Icecast ICY metadata blocks out of an audio stream.
 *
 * Every [metaInt] bytes of raw audio, the server sends 1 length byte `L`, followed by `L * 16`
 * bytes of ASCII/UTF-8 metadata (e.g., `StreamTitle='Artist - Song';`).
 * This stream strips those metadata blocks so only pure audio bytes reach the recording sink,
 * and invokes [onTrackMetadata] whenever a non-blank `StreamTitle` is parsed.
 */
internal class IcyDemuxInputStream(
    private val delegate: InputStream,
    private val metaInt: Int,
    private val initialAudioBytesOffset: Long = 0L,
    private val onTrackMetadata: (String) -> Unit = {},
    private val onTrackMetadataWithOffset: ((String, Long) -> Unit)? = null
) : InputStream() {

    private var bytesUntilMeta: Int = metaInt
    private var totalAudioBytesRead: Long = initialAudioBytesOffset

    override fun read(): Int {
        if (metaInt <= 0) return delegate.read()

        if (bytesUntilMeta == 0) {
            if (!readAndDispatchMetadata()) return -1
            bytesUntilMeta = metaInt
        }

        val b = delegate.read()
        if (b != -1) {
            bytesUntilMeta--
            totalAudioBytesRead++
        }
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (metaInt <= 0) return delegate.read(b, off, len)

        if (bytesUntilMeta == 0) {
            if (!readAndDispatchMetadata()) return -1
            bytesUntilMeta = metaInt
        }

        val maxToRead = minOf(len, bytesUntilMeta)
        val bytesRead = delegate.read(b, off, maxToRead)
        if (bytesRead == -1) return -1

        bytesUntilMeta -= bytesRead
        totalAudioBytesRead += bytesRead
        return bytesRead
    }

    private fun readAndDispatchMetadata(): Boolean {
        val lengthByte = delegate.read()
        if (lengthByte == -1) return false

        val metaLength = (lengthByte and 0xFF) * 16
        if (metaLength == 0) return true

        val metaBuffer = ByteArray(metaLength)
        var totalRead = 0
        while (totalRead < metaLength) {
            val r = delegate.read(metaBuffer, totalRead, metaLength - totalRead)
            if (r == -1) return false
            totalRead += r
        }

        val rawMeta = decodeMetadataString(metaBuffer, totalRead)
        val streamTitle = extractStreamTitle(rawMeta)
        if (!streamTitle.isNullOrBlank()) {
            onTrackMetadata(streamTitle)
            onTrackMetadataWithOffset?.invoke(streamTitle, totalAudioBytesRead)
        }
        return true
    }

    override fun close() {
        delegate.close()
    }

    companion object {
        private val STREAM_TITLE_WITH_SEMI_REGEX = Regex("""StreamTitle='(.*?)';""", RegexOption.DOT_MATCHES_ALL)
        private val STREAM_TITLE_FALLBACK_REGEX = Regex("""StreamTitle='([^']*)'""")

        internal fun decodeMetadataString(bytes: ByteArray, length: Int): String {
            var effectiveLen = length
            while (effectiveLen > 0 && bytes[effectiveLen - 1] == 0.toByte()) {
                effectiveLen--
            }
            if (effectiveLen <= 0) return ""
            return try {
                val utf8 = String(bytes, 0, effectiveLen, Charsets.UTF_8)
                if (utf8.contains('\uFFFD')) {
                    String(bytes, 0, effectiveLen, Charsets.ISO_8859_1)
                } else {
                    utf8
                }
            } catch (_: Exception) {
                String(bytes, 0, effectiveLen, Charsets.ISO_8859_1)
            }
        }

        internal fun extractStreamTitle(metaString: String): String? {
            if (metaString.isBlank()) return null
            val match = STREAM_TITLE_WITH_SEMI_REGEX.find(metaString)
                ?: STREAM_TITLE_FALLBACK_REGEX.find(metaString)
            return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}
