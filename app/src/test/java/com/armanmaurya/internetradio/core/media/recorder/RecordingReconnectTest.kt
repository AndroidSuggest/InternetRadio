package com.armanmaurya.internetradio.core.media.recorder

import com.armanmaurya.internetradio.core.media.recorder.backend.OggRemuxState
import com.armanmaurya.internetradio.core.media.recorder.backend.OggRewriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RecordingReconnectTest {

    @Test
    fun recordingConfig_hasCorrectDefaultRetryParameters() {
        val config = RecordingConfig(
            url = "http://example.com/stream",
            title = "Test Station"
        )

        assertEquals(300_000L, config.maxRetryDurationMs)
        assertEquals(1000L, config.minRetryDelayMs)
        assertEquals(15_000L, config.maxRetryDelayMs)
    }

    @Test
    fun oggRewriter_maintainsSingleContinuousBitstreamAcrossReconnections() {
        val initialSerial = 12345
        val reconnectedSerial = 67890

        // Build pages for stream 1: 3 headers (BOS=2) + 2 audio pages
        val stream1Bytes = ByteArrayOutputStream().apply {
            write(buildOggPage(headerType = 0x02, serial = initialSerial, seq = 0, granule = 0, data = "vorbis-bos".toByteArray()))
            write(buildOggPage(headerType = 0x00, serial = initialSerial, seq = 1, granule = 0, data = "vorbis-comments".toByteArray()))
            write(buildOggPage(headerType = 0x00, serial = initialSerial, seq = 2, granule = 0, data = "vorbis-setup".toByteArray()))
            write(buildOggPage(headerType = 0x00, serial = initialSerial, seq = 3, granule = 1000, data = "audio-chunk-1".toByteArray()))
            write(buildOggPage(headerType = 0x00, serial = initialSerial, seq = 4, granule = 2000, data = "audio-chunk-2".toByteArray()))
        }.toByteArray()

        // Build pages for reconnected stream 2: 3 headers (BOS=2) + 2 audio pages with new serial and restart granule
        val stream2Bytes = ByteArrayOutputStream().apply {
            write(buildOggPage(headerType = 0x02, serial = reconnectedSerial, seq = 0, granule = 5000, data = "vorbis-bos-2".toByteArray()))
            write(buildOggPage(headerType = 0x00, serial = reconnectedSerial, seq = 1, granule = 5000, data = "vorbis-comments-2".toByteArray()))
            write(buildOggPage(headerType = 0x00, serial = reconnectedSerial, seq = 2, granule = 5000, data = "vorbis-setup-2".toByteArray()))
            write(buildOggPage(headerType = 0x00, serial = reconnectedSerial, seq = 3, granule = 5000, data = "audio-chunk-3".toByteArray()))
            write(buildOggPage(headerType = 0x00, serial = reconnectedSerial, seq = 4, granule = 6500, data = "audio-chunk-4".toByteArray()))
        }.toByteArray()

        val output = ByteArrayOutputStream()
        val oggState = OggRemuxState()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        var lastAudioProgressMs = -1L

        // Remux initial stream
        OggRewriter.remuxStream(
            input = ByteArrayInputStream(stream1Bytes),
            output = output,
            scope = scope,
            job = null,
            state = oggState,
            onAudioProgress = { lastAudioProgressMs = it }
        ) {}

        assertTrue(oggState.initialSerialNumber == initialSerial)
        assertEquals(4L, oggState.lastPageSequence)
        assertEquals(1000L, oggState.lastGranulePosition)
        assertEquals((1000L * 1000L) / 48000L, lastAudioProgressMs)
        assertFalse(oggState.isInitialStream)

        // Remux reconnected stream (simulating resumption)
        OggRewriter.remuxStream(
            input = ByteArrayInputStream(stream2Bytes),
            output = output,
            scope = scope,
            job = null,
            state = oggState,
            onAudioProgress = { lastAudioProgressMs = it }
        ) {}

        // Stream 2 skipped 3 duplicate headers, and wrote 2 audio pages
        // Page sequence should increment to 6 (4 + 2)
        assertEquals(6L, oggState.lastPageSequence)
        // Granule should be continuous: 1000 + (6500 - 5000) = 2500
        assertEquals(2500L, oggState.lastGranulePosition)
        assertEquals((2500L * 1000L) / 48000L, lastAudioProgressMs)

        // Parse all pages written into output and verify continuous single bitstream
        val writtenPages = parseOggPages(output.toByteArray())
        // Total pages written: 5 (from stream 1) + 2 (from stream 2, since 3 headers were skipped) = 7
        assertEquals(7, writtenPages.size)

        // All written pages MUST have the SAME initial serial number
        for (page in writtenPages) {
            assertEquals(initialSerial, page.serial)
        }

        // Only page 0 should have the BOS flag (0x02). No subsequent page can have BOS
        assertEquals(0x02, writtenPages[0].headerType.toInt())
        for (i in 1 until writtenPages.size) {
            assertEquals(0x00, writtenPages[i].headerType.toInt() and 0x02)
        }

        // Page sequences must be contiguous: 0, 1, 2, 3, 4, 5, 6
        for (i in writtenPages.indices) {
            assertEquals(i.toLong(), writtenPages[i].seq)
        }
    }

    @Test
    fun icyDemuxInputStream_stripsMetadataAndPreventsDuplicateTrackLoggingAcrossReconnects() {
        val metaInt = 16
        val audioChunk1 = ByteArray(16) { 0x11 }
        val audioChunk2 = ByteArray(16) { 0x22 }
        val audioChunk3 = ByteArray(16) { 0x33 }

        val metaSong1 = buildIcyMetaBlock("StreamTitle='Queen - Bohemian Rhapsody';")
        val metaSong2 = buildIcyMetaBlock("StreamTitle='Queen - Under Pressure';")

        // Initial stream: plays Song 1
        val stream1Bytes = ByteArrayOutputStream().apply {
            write(audioChunk1)
            write(metaSong1)
            write(audioChunk2)
        }.toByteArray()

        // Reconnected stream: server resends current playing Song 1, then transitions to Song 2
        val stream2Bytes = ByteArrayOutputStream().apply {
            write(audioChunk2)
            write(metaSong1) // Resent by server upon reconnect!
            write(audioChunk3)
            write(metaSong2) // New song
            write(audioChunk1)
        }.toByteArray()

        val capturedTracks = mutableListOf<String>()
        var lastRawTrack: String? = null
        val audioOutput = ByteArrayOutputStream()

        val onTrackMetadata: (String) -> Unit = { raw ->
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty() && trimmed != lastRawTrack) {
                lastRawTrack = trimmed
                capturedTracks.add(trimmed)
            }
        }

        // 1. Process Stream 1
        val demux1 = com.armanmaurya.internetradio.core.media.recorder.backend.IcyDemuxInputStream(
            delegate = ByteArrayInputStream(stream1Bytes),
            metaInt = metaInt,
            onTrackMetadata = onTrackMetadata
        )
        val buf = ByteArray(8)
        while (true) {
            val r = demux1.read(buf)
            if (r == -1) break
            audioOutput.write(buf, 0, r)
        }

        assertEquals(listOf("Queen - Bohemian Rhapsody"), capturedTracks)

        // 2. Disconnect & Reconnect: Process Stream 2 with fresh IcyDemuxInputStream
        val demux2 = com.armanmaurya.internetradio.core.media.recorder.backend.IcyDemuxInputStream(
            delegate = ByteArrayInputStream(stream2Bytes),
            metaInt = metaInt,
            onTrackMetadata = onTrackMetadata
        )
        while (true) {
            val r = demux2.read(buf)
            if (r == -1) break
            audioOutput.write(buf, 0, r)
        }

        // Verify: Song 1 was NOT duplicated on reconnect! Only Bohemian Rhapsody followed by Under Pressure
        assertEquals(
            listOf("Queen - Bohemian Rhapsody", "Queen - Under Pressure"),
            capturedTracks
        )

        // Verify: Audio output contains ONLY pure audio bytes, ZERO ICY metadata bytes leaked
        val expectedAudio = audioChunk1 + audioChunk2 + audioChunk2 + audioChunk3 + audioChunk1
        org.junit.Assert.assertArrayEquals(expectedAudio, audioOutput.toByteArray())
    }

    private fun buildIcyMetaBlock(meta: String): ByteArray {
        val raw = meta.toByteArray(Charsets.UTF_8)
        val blocks = (raw.size + 15) / 16
        val padded = ByteArray(1 + blocks * 16)
        padded[0] = blocks.toByte()
        System.arraycopy(raw, 0, padded, 1, raw.size)
        return padded
    }

    private data class ParsedOggPage(
        val headerType: Byte,
        val granule: Long,
        val serial: Int,
        val seq: Long
    )

    private fun parseOggPages(bytes: ByteArray): List<ParsedOggPage> {
        val pages = mutableListOf<ParsedOggPage>()
        var offset = 0
        while (offset + 27 <= bytes.size) {
            if (bytes[offset] == 0x4F.toByte() && bytes[offset + 1] == 0x67.toByte() &&
                bytes[offset + 2] == 0x67.toByte() && bytes[offset + 3] == 0x53.toByte()
            ) {
                val headerType = bytes[offset + 5]
                val granule = ByteBuffer.wrap(bytes, offset + 6, 8).order(ByteOrder.LITTLE_ENDIAN).long
                val serial = ByteBuffer.wrap(bytes, offset + 14, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val seq = ByteBuffer.wrap(bytes, offset + 18, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val segCount = bytes[offset + 26].toInt() and 0xFF
                var dataLen = 0
                for (i in 0 until segCount) {
                    dataLen += bytes[offset + 27 + i].toInt() and 0xFF
                }
                pages.add(ParsedOggPage(headerType, granule, serial, seq))
                offset += 27 + segCount + dataLen
            } else {
                offset++
            }
        }
        return pages
    }

    private fun buildOggPage(headerType: Int, serial: Int, seq: Int, granule: Long, data: ByteArray): ByteArray {
        val segCount = if (data.isEmpty()) 0 else (data.size + 254) / 255
        val segTable = ByteArray(segCount)
        var remaining = data.size
        for (i in 0 until segCount) {
            val len = minOf(remaining, 255)
            segTable[i] = len.toByte()
            remaining -= len
        }

        val header = ByteArray(27)
        header[0] = 0x4F.toByte() // 'O'
        header[1] = 0x67.toByte() // 'g'
        header[2] = 0x67.toByte() // 'g'
        header[3] = 0x53.toByte() // 'S'
        header[4] = 0 // version
        header[5] = headerType.toByte()

        ByteBuffer.wrap(header, 6, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(granule)
        ByteBuffer.wrap(header, 14, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(serial)
        ByteBuffer.wrap(header, 18, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(seq)
        header[26] = segCount.toByte()

        return ByteArrayOutputStream().apply {
            write(header)
            write(segTable)
            write(data)
        }.toByteArray()
    }
}
