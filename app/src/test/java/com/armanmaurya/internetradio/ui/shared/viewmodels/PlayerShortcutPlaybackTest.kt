package com.armanmaurya.internetradio.ui.shared.viewmodels

import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.model.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerShortcutPlaybackTest {

    private fun createStation(uuid: String, name: String): RadioStation {
        return RadioStation(
            changeUuid = "change-$uuid",
            stationUuid = uuid,
            name = name,
            url = "http://stream.example.com/$uuid",
            urlResolved = "http://stream.example.com/$uuid",
            homepage = "",
            favicon = "",
            tags = emptyList(),
            country = "US",
            countryCode = "US",
            state = "",
            iso3166_2 = null,
            language = "en",
            languageCodes = emptyList(),
            votes = 100,
            lastChangeTime = "",
            codec = "MP3",
            bitrate = 128,
            lastCheckOk = true,
            lastCheckTime = "",
            lastCheckOkTime = "",
            lastLocalCheckTime = "",
            clickTimestamp = "",
            clickCount = 10,
            clickTrend = 1,
            sslError = false,
            geoLat = null,
            geoLong = null,
            geoDistance = null,
            hasExtendedInfo = false,
            isCustom = false
        )
    }

    @Test
    fun shortcutPlayback_libraryStation_enablesNextAndPreviousControls() {
        val stationA = createStation("station-a", "Rock FM")
        val stationB = createStation("station-b", "Jazz FM")
        val stationC = createStation("station-c", "Pop FM")

        val libraryStations = listOf(stationA, stationB, stationC)
        val targetUuid = "station-b"
        val libraryIndex = libraryStations.indexOfFirst { it.stationUuid == targetUuid }

        assertEquals(1, libraryIndex)

        // When in library, playlist is set to libraryStations
        val state = PlayerUiState(
            currentStation = libraryStations[libraryIndex],
            currentPlaylist = libraryStations,
            currentPlaylistIndex = libraryIndex,
            playbackSource = PlaybackSource.Library,
            hasNext = libraryStations.size > 1,
            hasPrevious = libraryStations.size > 1
        )

        // Left and right arrows are enabled
        assertTrue(state.hasNext)
        assertTrue(state.hasPrevious)
        assertEquals("Jazz FM", state.currentStation?.name)
        assertEquals(PlaybackSource.Library, state.playbackSource)
    }

    @Test
    fun shortcutPlayback_nonLibraryStation_disablesNextAndPreviousControls() {
        val nonLibraryStation = createStation("station-external", "External Indie FM")
        val libraryStations = listOf(createStation("station-a", "Rock FM"))

        val targetUuid = "station-external"
        val libraryIndex = libraryStations.indexOfFirst { it.stationUuid == targetUuid }

        assertEquals(-1, libraryIndex)

        // When not in library, playlist is isolated to single station
        val playlist = listOf(nonLibraryStation)
        val state = PlayerUiState(
            currentStation = nonLibraryStation,
            currentPlaylist = playlist,
            currentPlaylistIndex = 0,
            playbackSource = PlaybackSource.None,
            hasNext = playlist.size > 1,
            hasPrevious = playlist.size > 1
        )

        // Left and right arrows remain disabled
        assertFalse(state.hasNext)
        assertFalse(state.hasPrevious)
        assertEquals("External Indie FM", state.currentStation?.name)
        assertEquals(PlaybackSource.None, state.playbackSource)
    }
}
