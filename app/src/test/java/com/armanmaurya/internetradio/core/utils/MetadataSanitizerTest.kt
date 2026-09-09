package com.armanmaurya.internetradio.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataSanitizerTest {

    @Test
    fun testIsDiscardableSegment() {
        // Blank
        assertTrue(MetadataSanitizer.isDiscardableSegment(""))
        assertTrue(MetadataSanitizer.isDiscardableSegment("   "))

        // UUID
        assertTrue(MetadataSanitizer.isDiscardableSegment("522b37ba-e2b7-4769-96e4-fde1c44c9da9"))
        assertTrue(MetadataSanitizer.isDiscardableSegment("c67d8cfb-c431-4878-9368-6a0886c793fc"))

        // Hashes
        assertTrue(MetadataSanitizer.isDiscardableSegment("e4d909c290d0fb1ca068ffaddf22cbd0")) // MD5
        assertTrue(MetadataSanitizer.isDiscardableSegment("a1b2c3d4e5f6789012345678")) // 24-char hex

        // URLs & domains
        assertTrue(MetadataSanitizer.isDiscardableSegment("http://streaming.hotmixradio.com"))
        assertTrue(MetadataSanitizer.isDiscardableSegment("https://hotmixradio.com/"))
        assertTrue(MetadataSanitizer.isDiscardableSegment("www.hotmixradio.com"))
        assertTrue(MetadataSanitizer.isDiscardableSegment("station.fm"))
        assertTrue(MetadataSanitizer.isDiscardableSegment("radio.net"))

        // Legitimate song / artist names must NOT be discarded
        assertFalse(MetadataSanitizer.isDiscardableSegment("NORAH DAVIS"))
        assertFalse(MetadataSanitizer.isDiscardableSegment("All Of Me - Acoustic"))
        assertFalse(MetadataSanitizer.isDiscardableSegment("Queen"))
        assertFalse(MetadataSanitizer.isDiscardableSegment("Bohemian Rhapsody"))
        assertFalse(MetadataSanitizer.isDiscardableSegment("Spider-Man"))
    }

    @Test
    fun testHotmixRadioMetadata() {
        val raw = "NORAH DAVIS - All Of Me - Acoustic ||  || S || 522b37ba-e2b7-4769-96e4-fde1c44c9da9"
        val parsed = MetadataSanitizer.cleanAndParse(raw)

        assertEquals("NORAH DAVIS", parsed.artist)
        assertEquals("All Of Me - Acoustic", parsed.title)
        assertEquals("NORAH DAVIS - All Of Me - Acoustic", parsed.combinedDisplay)
    }

    @Test
    fun testHotmixKyleLionhartMetadata() {
        val raw = "KYLE LIONHART & EMILY REID - Sorry I'm Gone ||  || S || c67d8cfb-c431-4878-9368-6a0886c793fc"
        val parsed = MetadataSanitizer.cleanAndParse(raw)

        assertEquals("KYLE LIONHART & EMILY REID", parsed.artist)
        assertEquals("Sorry I'm Gone", parsed.title)
        assertEquals("KYLE LIONHART & EMILY REID - Sorry I'm Gone", parsed.combinedDisplay)
    }

    @Test
    fun testStandardArtistTitle() {
        val raw = "Queen - Bohemian Rhapsody"
        val parsed = MetadataSanitizer.cleanAndParse(raw)

        assertEquals("Queen", parsed.artist)
        assertEquals("Bohemian Rhapsody", parsed.title)
        assertEquals("Queen - Bohemian Rhapsody", parsed.combinedDisplay)
    }

    @Test
    fun testTrailingUrlSegment() {
        val raw = "Oasis - Wonderwall - www.station.fm"
        val parsed = MetadataSanitizer.cleanAndParse(raw)

        assertEquals("Oasis", parsed.artist)
        assertEquals("Wonderwall", parsed.title)
        assertEquals("Oasis - Wonderwall", parsed.combinedDisplay)
    }

    @Test
    fun testTrailingUuidSegment() {
        val raw = "Oasis - Wonderwall - 522b37ba-e2b7-4769-96e4-fde1c44c9da9"
        val parsed = MetadataSanitizer.cleanAndParse(raw)

        assertEquals("Oasis", parsed.artist)
        assertEquals("Wonderwall", parsed.title)
        assertEquals("Oasis - Wonderwall", parsed.combinedDisplay)
    }

    @Test
    fun testPipeSeparatedArtistTitle() {
        val raw = "Coldplay || Yellow"
        val parsed = MetadataSanitizer.cleanAndParse(raw)

        assertEquals("Coldplay", parsed.artist)
        assertEquals("Yellow", parsed.title)
        assertEquals("Coldplay - Yellow", parsed.combinedDisplay)
    }

    @Test
    fun testSongWithMultipleHyphens() {
        val raw = "Post Malone - Cooped Up - Return of the Mack"
        val parsed = MetadataSanitizer.cleanAndParse(raw)

        assertEquals("Post Malone", parsed.artist)
        assertEquals("Cooped Up - Return of the Mack", parsed.title)
        assertEquals("Post Malone - Cooped Up - Return of the Mack", parsed.combinedDisplay)
    }
}
