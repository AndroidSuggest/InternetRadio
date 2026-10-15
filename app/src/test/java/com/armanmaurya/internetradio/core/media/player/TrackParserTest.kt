package com.armanmaurya.internetradio.core.media.player

import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class TrackParserTest {

    @Test
    fun testEmptyMetadataReturnsNull() {
        val metadata = Metadata()
        assertNull(TrackParser.parse(metadata))
    }

    @Test
    fun testIcyInfoStandard() {
        val icy = IcyInfo(ByteArray(0), "Queen - Bohemian Rhapsody", "http://example.com")
        val metadata = Metadata(icy)
        assertEquals("Queen - Bohemian Rhapsody", TrackParser.parse(metadata))
    }

    @Test
    fun testIcyInfoWithStreamTitleWrapper() {
        val icy = IcyInfo(ByteArray(0), "StreamTitle='Daft Punk - One More Time';StreamUrl='';", null)
        val metadata = Metadata(icy)
        assertEquals("Daft Punk - One More Time", TrackParser.parse(metadata))
    }

    @Test
    fun testIcyInfoBlankReturnsNull() {
        val icy = IcyInfo(ByteArray(0), "   ", null)
        val metadata = Metadata(icy)
        assertNull(TrackParser.parse(metadata))
    }

    @Test
    fun testId3TitleOnly() {
        val tit2 = TextInformationFrame("TIT2", null, ImmutableList.of("Venus Dna"))
        val metadata = Metadata(tit2)
        assertEquals("Venus Dna", TrackParser.parse(metadata))
    }

    @Test
    fun testId3SeparateTitleAndArtist() {
        val tit2 = TextInformationFrame("TIT2", null, ImmutableList.of("Venus Dna"))
        val tpe1 = TextInformationFrame("TPE1", null, ImmutableList.of("Fresh Moods"))
        val metadata = Metadata(tpe1, tit2)
        assertEquals("Venus Dna - Fresh Moods", TrackParser.parse(metadata))
    }

    @Test
    fun testId3TitleAlreadyContainsArtist() {
        val tit2 = TextInformationFrame("TIT2", null, ImmutableList.of("Fresh Moods - Venus Dna"))
        val tpe1 = TextInformationFrame("TPE1", null, ImmutableList.of("Fresh Moods"))
        val metadata = Metadata(tit2, tpe1)
        assertEquals("Fresh Moods - Venus Dna", TrackParser.parse(metadata))
    }

    @Test
    fun testId3v22Frames() {
        val tt2 = TextInformationFrame("TT2", null, ImmutableList.of("Yesterday"))
        val tp1 = TextInformationFrame("TP1", null, ImmutableList.of("The Beatles"))
        val metadata = Metadata(tt2, tp1)
        assertEquals("Yesterday - The Beatles", TrackParser.parse(metadata))
    }

    @Test
    fun testId3MultiValueArtist() {
        val tit2 = TextInformationFrame("TIT2", null, ImmutableList.of("My Universe"))
        val tpe1 = TextInformationFrame("TPE1", null, ImmutableList.of("Coldplay", "BTS"))
        val metadata = Metadata(tit2, tpe1)
        assertEquals("My Universe - Coldplay, BTS", TrackParser.parse(metadata))
    }

    @Test
    fun testId3UserDefinedStreamTitle() {
        val txxx = TextInformationFrame("TXXX", "StreamTitle", ImmutableList.of("Pink Floyd - Comfortably Numb"))
        val metadata = Metadata(txxx)
        assertEquals("Pink Floyd - Comfortably Numb", TrackParser.parse(metadata))
    }

    @Test
    fun testId3UserDefinedTitleAndArtist() {
        val txxxTitle = TextInformationFrame("TXXX", "TITLE", ImmutableList.of("Time"))
        val txxxArtist = TextInformationFrame("TXXX", "ARTIST", ImmutableList.of("Pink Floyd"))
        val metadata = Metadata(txxxTitle, txxxArtist)
        assertEquals("Time - Pink Floyd", TrackParser.parse(metadata))
    }

    @Test
    fun testCommentFrameWithStreamTitle() {
        val comm = CommentFrame("eng", "StreamTitle", "StreamTitle='Radiohead - Creep';")
        val metadata = Metadata(comm)
        assertEquals("Radiohead - Creep", TrackParser.parse(metadata))
    }

    @Test
    fun testInternalFrameWithStreamTitle() {
        val internal = InternalFrame("----", "StreamTitle", "Nirvana - Smells Like Teen Spirit")
        val metadata = Metadata(internal)
        assertEquals("Nirvana - Smells Like Teen Spirit", TrackParser.parse(metadata))
    }

    @Test
    fun testVorbisCommentTitleAndArtist() {
        val title = VorbisComment("TITLE", "Stairway to Heaven")
        val artist = VorbisComment("ARTIST", "Led Zeppelin")
        val metadata = Metadata(title, artist)
        assertEquals("Stairway to Heaven - Led Zeppelin", TrackParser.parse(metadata))
    }

    @Test
    fun testVorbisCommentTitleAlreadyContainsArtist() {
        val title = VorbisComment("TITLE", "Led Zeppelin - Stairway to Heaven")
        val artist = VorbisComment("ARTIST", "Led Zeppelin")
        val metadata = Metadata(title, artist)
        assertEquals("Led Zeppelin - Stairway to Heaven", TrackParser.parse(metadata))
    }

    @Test
    fun testVorbisCommentStreamTitle() {
        val streamTitle = VorbisComment("STREAMTITLE", "AC/DC - Back in Black")
        val metadata = Metadata(streamTitle)
        assertEquals("AC/DC - Back in Black", TrackParser.parse(metadata))
    }

    @Test
    fun testFallbackTitleAndArtistFrames() {
        val tit1 = TextInformationFrame("TIT1", null, ImmutableList.of("Symphony No. 5"))
        val tpe2 = TextInformationFrame("TPE2", null, ImmutableList.of("Beethoven"))
        val metadata = Metadata(tit1, tpe2)
        assertEquals("Symphony No. 5 - Beethoven", TrackParser.parse(metadata))
    }

    @Test
    fun testBlankAndEmptyFramesHandledGracefully() {
        val tit2Blank = TextInformationFrame("TIT2", null, ImmutableList.of("  "))
        val metadata = Metadata(tit2Blank)
        assertNull(TrackParser.parse(metadata))
    }
}
