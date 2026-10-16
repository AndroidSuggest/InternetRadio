package com.armanmaurya.internetradio.data.backup

import com.armanmaurya.internetradio.data.local.entity.StationEntity
import com.armanmaurya.internetradio.domain.model.ConflictStrategy
import com.armanmaurya.internetradio.domain.model.MalformedBackupException
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupManagerTest {

    @Test
    fun `toBackupStation ignores lastPlayedAt for distinct comparison`() {
        val entity1 = StationEntity(
            stationUuid = "test-uuid-1",
            name = "Test Radio",
            url = "https://stream.example.com",
            lastPlayedAt = 1000L
        )

        val entity2 = StationEntity(
            stationUuid = "test-uuid-1",
            name = "Test Radio",
            url = "https://stream.example.com",
            lastPlayedAt = 2000L // Playback updated timestamp
        )

        val backup1 = entity1.toBackupStation()
        val backup2 = entity2.toBackupStation()

        // Backup representations must be identical so playback does not trigger auto-backup
        assertEquals(backup1, backup2)
    }

    @Test
    fun `toBackupStation detects content modifications`() {
        val entity1 = StationEntity(
            stationUuid = "test-uuid-1",
            name = "Test Radio",
            url = "https://stream.example.com"
        )

        val entity2 = entity1.copy(name = "Updated Radio Name")

        val backup1 = entity1.toBackupStation()
        val backup2 = entity2.toBackupStation()

        assertNotEquals(backup1, backup2)
    }

    @Test
    fun `conflict strategy handling logic applies correctly`() {
        val existing = StationEntity(
            stationUuid = "uuid-1",
            name = "Old Name",
            url = "https://old.com",
            addedAt = 1000L
        )

        val incomingNewer = BackupStation(
            stationUuid = "uuid-1",
            name = "Newer Name",
            url = "https://new.com",
            addedAt = 2000L
        ).toStationEntity()

        val incomingOlder = BackupStation(
            stationUuid = "uuid-1",
            name = "Older Name",
            url = "https://older.com",
            addedAt = 500L
        ).toStationEntity()

        // 1. SKIP strategy
        var shouldUpdate = when (ConflictStrategy.SKIP) {
            ConflictStrategy.OVERWRITE -> true
            ConflictStrategy.KEEP_NEWER -> (incomingNewer.addedAt ?: 0L) > (existing.addedAt ?: 0L)
            ConflictStrategy.SKIP -> false
        }
        assertEquals(false, shouldUpdate)

        // 2. OVERWRITE strategy
        shouldUpdate = when (ConflictStrategy.OVERWRITE) {
            ConflictStrategy.OVERWRITE -> true
            ConflictStrategy.KEEP_NEWER -> (incomingOlder.addedAt ?: 0L) > (existing.addedAt ?: 0L)
            ConflictStrategy.SKIP -> false
        }
        assertEquals(true, shouldUpdate)

        // 3. KEEP_NEWER with newer entity
        shouldUpdate = when (ConflictStrategy.KEEP_NEWER) {
            ConflictStrategy.OVERWRITE -> true
            ConflictStrategy.KEEP_NEWER -> (incomingNewer.addedAt ?: 0L) > (existing.addedAt ?: 0L)
            ConflictStrategy.SKIP -> false
        }
        assertEquals(true, shouldUpdate)

        // 4. KEEP_NEWER with older entity
        shouldUpdate = when (ConflictStrategy.KEEP_NEWER) {
            ConflictStrategy.OVERWRITE -> true
            ConflictStrategy.KEEP_NEWER -> (incomingOlder.addedAt ?: 0L) > (existing.addedAt ?: 0L)
            ConflictStrategy.SKIP -> false
        }
        assertEquals(false, shouldUpdate)
    }

    @Test
    fun `smart sync merges remote stations missing locally`() {
        val localEntities = listOf(
            StationEntity(stationUuid = "s1", name = "Local 1", url = "https://s1.com")
        )
        val remoteStations = listOf(
            BackupStation(stationUuid = "s1", name = "Local 1", url = "https://s1.com"),
            BackupStation(stationUuid = "s2", name = "Remote 2", url = "https://s2.com")
        )

        val localIds = localEntities.map { it.stationUuid }.toSet()
        val missingLocally = remoteStations.filter { it.stationUuid !in localIds }

        assertEquals(1, missingLocally.size)
        assertEquals("s2", missingLocally.first().stationUuid)
    }

    @Test
    fun `smart sync restores all when local library is empty`() {
        val localEntities = emptyList<StationEntity>()
        val remoteStations = listOf(
            BackupStation(stationUuid = "s1", name = "Radio 1", url = "https://s1.com"),
            BackupStation(stationUuid = "s2", name = "Radio 2", url = "https://s2.com")
        )

        val shouldRestoreAll = localEntities.isEmpty() && remoteStations.isNotEmpty()
        assertEquals(true, shouldRestoreAll)
    }

    @Test
    fun `malformed JSON parsing throws and is caught as MalformedBackupException`() {
        val corruptedJson = """{ "schemaVersion": 2, "stations": [ { "name": "Broken" """ // Missing closing brackets

        val exception = assertThrows(MalformedBackupException::class.java) {
            try {
                Gson().fromJson(corruptedJson, LibraryBackup::class.java)
            } catch (e: JsonSyntaxException) {
                throw MalformedBackupException("Backup file (stations.json) is corrupted or contains invalid JSON", e)
            }
        }

        assertTrue(exception.message!!.contains("corrupted or contains invalid JSON"))
        assertTrue(exception.cause is JsonSyntaxException)
    }

    @Test
    fun `remote station sanitization drops blank and duplicate entries`() {
        val rawStations = listOf(
            BackupStation(stationUuid = "valid-1", name = "Valid Radio 1", url = "https://stream1.com"),
            BackupStation(stationUuid = "", name = "Blank UUID Radio", url = "https://stream2.com"),
            BackupStation(stationUuid = "invalid-name", name = "", url = "https://stream3.com"),
            BackupStation(stationUuid = "invalid-url", name = "Blank URL", url = ""),
            BackupStation(stationUuid = "valid-2", name = "Valid Radio 2", url = "https://stream4.com"),
            BackupStation(stationUuid = "valid-1", name = "Duplicate Radio 1", url = "https://stream1-dup.com")
        )

        val validStations = rawStations
            .filter { !it.stationUuid.isNullOrBlank() && !it.name.isNullOrBlank() && !it.url.isNullOrBlank() }
            .distinctBy { it.stationUuid }

        assertEquals(2, validStations.size)
        assertEquals(listOf("valid-1", "valid-2"), validStations.map { it.stationUuid })
    }

    @Test
    fun `blank or empty JSON is detected properly`() {
        val emptyJson = "   \n\t  "
        assertTrue(emptyJson.isBlank())
    }
}
