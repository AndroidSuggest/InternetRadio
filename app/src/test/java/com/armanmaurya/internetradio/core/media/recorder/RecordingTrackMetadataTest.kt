package com.armanmaurya.internetradio.core.media.recorder

import com.armanmaurya.internetradio.core.media.recorder.backend.HlsPlaylistParser
import com.armanmaurya.internetradio.core.media.recorder.backend.IcyDemuxInputStream
import com.armanmaurya.internetradio.core.media.recorder.backend.OggRewriter
import com.armanmaurya.internetradio.data.recording.RecordingSidecarDto
import com.armanmaurya.internetradio.domain.model.RecordingTrack
import com.google.gson.Gson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RecordingTrackMetadataTest {

    @Test
    fun icyDemuxInputStream_stripsMetadataAndExtractsStreamTitles() {
        val metaInt = 8
        val audioChunk1 = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val audioChunk2 = byteArrayOf(9, 10, 11, 12, 13, 14, 15, 16)
        val audioChunk3 = byteArrayOf(17, 18, 19, 20)

        val metaBlock1 = buildIcyMetaBlock("StreamTitle='Daft Punk - Get Lucky';")
        val metaBlockEmpty = byteArrayOf(0) // 0 * 16 bytes
        val metaBlock2 = buildIcyMetaBlock("StreamTitle='Journey - Don't Stop Believin'';")

        val streamBytes = ByteArrayOutputStream().apply {
            write(audioChunk1)
            write(metaBlock1)
            write(audioChunk2)
            write(metaBlockEmpty)
            write(audioChunk1)
            write(metaBlock2)
            write(audioChunk3)
        }.toByteArray()

        val capturedTracks = mutableListOf<String>()
        val demuxStream = IcyDemuxInputStream(
            delegate = ByteArrayInputStream(streamBytes),
            metaInt = metaInt,
            onTrackMetadata = { capturedTracks.add(it) }
        )

        val audioOutput = ByteArrayOutputStream()
        val buf = ByteArray(5) // Intentionally unaligned buffer size
        while (true) {
            val r = demuxStream.read(buf)
            if (r == -1) break
            audioOutput.write(buf, 0, r)
        }

        val expectedAudio = audioChunk1 + audioChunk2 + audioChunk1 + audioChunk3
        assertArrayEquals(expectedAudio, audioOutput.toByteArray())
        assertEquals(
            listOf("Daft Punk - Get Lucky", "Journey - Don't Stop Believin'"),
            capturedTracks
        )
    }

    @Test
    fun oggRewriter_extractOggTrackTitle_parsesVorbisComments() {
        val vendor = "Xiph.Org libVorbis".toByteArray(Charsets.UTF_8)
        val c1 = "ARTIST=Radiohead".toByteArray(Charsets.UTF_8)
        val c2 = "TITLE=Creep".toByteArray(Charsets.UTF_8)

        val buf = ByteBuffer.allocate(7 + 4 + vendor.size + 4 + 4 + c1.size + 4 + c2.size + 1)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x03.toByte())
        buf.put("vorbis".toByteArray(Charsets.US_ASCII))
        buf.putInt(vendor.size)
        buf.put(vendor)
        buf.putInt(2)
        buf.putInt(c1.size)
        buf.put(c1)
        buf.putInt(c2.size)
        buf.put(c2)
        buf.put(1.toByte())

        val bytes = buf.array()
        val extracted = OggRewriter.extractOggTrackTitle(bytes, bytes.size)
        assertEquals("Radiohead - Creep", extracted)
    }

    @Test
    fun hlsPlaylistParser_extractsExtInfTrackTitles() {
        val m3u8 = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:100
            #EXTINF:6.0,Coldplay - Yellow
            seg100.aac
            #EXTINF:6.0,no desc
            seg101.aac
        """.trimIndent()

        val parsed = HlsPlaylistParser.parse(m3u8, "https://radio.example.com/live/index.m3u8")
        assertEquals(2, parsed.segments.size)
        assertEquals("Coldplay - Yellow", parsed.segments[0].title)
        assertEquals(null, parsed.segments[1].title)
    }

    @Test
    fun sidecarDto_roundTripSerialization_preservesTrackTimeline() {
        val gson = Gson()
        val dto = RecordingSidecarDto(
            stationName = "Synthwave FM",
            fileName = "Synthwave FM 2026-10-20.mp3",
            tracks = listOf(
                RecordingTrack(
                    title = "Nightcall",
                    artist = "Kavinsky",
                    rawTitle = "Kavinsky - Nightcall",
                    coverArtUrl = "https://example.com/cover1.jpg",
                    offsetMs = 0L,
                    timestampMs = 1700000000000L
                ),
                RecordingTrack(
                    title = "Tech Noir",
                    artist = "GUNSHIP",
                    rawTitle = "GUNSHIP - Tech Noir",
                    coverArtUrl = "https://example.com/cover2.jpg",
                    offsetMs = 215000L,
                    timestampMs = 1700000215000L
                )
            )
        )

        val json = gson.toJson(dto)
        val restored = gson.fromJson(json, RecordingSidecarDto::class.java)

        assertEquals(dto.stationName, restored.stationName)
        assertEquals(dto.fileName, restored.fileName)
        assertEquals(2, restored.tracks.size)
        assertEquals("Nightcall", restored.tracks[0].title)
        assertEquals("Kavinsky", restored.tracks[0].artist)
        assertEquals(215000L, restored.tracks[1].offsetMs)
    }

    private fun buildIcyMetaBlock(meta: String): ByteArray {
        val raw = meta.toByteArray(Charsets.UTF_8)
        val blocks = (raw.size + 15) / 16
        val padded = ByteArray(1 + blocks * 16)
        padded[0] = blocks.toByte()
        System.arraycopy(raw, 0, padded, 1, raw.size)
        return padded
    }
}
