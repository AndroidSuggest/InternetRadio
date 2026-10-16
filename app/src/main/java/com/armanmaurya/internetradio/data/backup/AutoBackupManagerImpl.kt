package com.armanmaurya.internetradio.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.armanmaurya.internetradio.core.system.SystemFacade
import com.armanmaurya.internetradio.domain.controller.AutoBackupManager
import com.armanmaurya.internetradio.domain.model.AutoBackupStatus
import com.armanmaurya.internetradio.domain.model.ConflictStrategy
import com.armanmaurya.internetradio.domain.model.MalformedBackupException
import com.armanmaurya.internetradio.domain.repository.LibraryRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AutoBackupManager"
private const val BACKUP_FILE_NAME = "stations.json"

@Singleton
class AutoBackupManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val systemFacade: SystemFacade
) : AutoBackupManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isInitialized = false
    private val syncMutex = Mutex()

    @OptIn(FlowPreview::class)
    override fun initialize() {
        if (isInitialized) return
        isInitialized = true

        scope.launch {
            val initialPrefs = settingsRepository.appPreferencesFlow.first()
            if (initialPrefs.isAutoBackupEnabled && !initialPrefs.autoBackupTreeUri.isNullOrBlank()) {
                val currentEntities = libraryRepository.getAllStationEntities()
                if (currentEntities.isEmpty()) {
                    Log.d(TAG, "Empty library detected on startup with active auto-backup folder, attempting auto-restore")
                    performRestore()
                }
            }

            libraryRepository.getAllStationEntitiesFlow()
                .map { entities -> entities.map { it.toBackupStation() } }
                .distinctUntilChanged()
                .drop(1)
                .debounce(2500L)
                .collect {
                    val prefs = settingsRepository.appPreferencesFlow.first()
                    if (prefs.isAutoBackupEnabled && !prefs.autoBackupTreeUri.isNullOrBlank()) {
                        Log.d(TAG, "Library change detected, triggering auto-backup to $BACKUP_FILE_NAME")
                        performBackup()
                    }
                }
        }
    }

    override suspend fun performBackup(): Result<Unit> = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            performBackupInternal()
        }
    }

    private suspend fun performBackupInternal(): Result<Unit> {
        try {
            val prefs = settingsRepository.appPreferencesFlow.first()
            val uriStr = prefs.autoBackupTreeUri
            if (uriStr.isNullOrBlank()) {
                val error = "No backup folder configured"
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(IllegalStateException(error))
            }

            val treeUri = Uri.parse(uriStr)
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isWritePermission
            }
            if (!hasPermission) {
                val error = "Storage permission revoked. Please re-select backup folder."
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(SecurityException(error))
            }

            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (treeDoc == null || !treeDoc.exists() || !treeDoc.isDirectory) {
                val error = "Backup folder no longer exists. Please re-select a folder."
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(FileNotFoundException(error))
            }

            if (!treeDoc.canWrite()) {
                val error = "Cannot write to selected backup folder. Permission denied."
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(SecurityException(error))
            }

            val targetFile = treeDoc.findFile(BACKUP_FILE_NAME)
                ?: treeDoc.createFile("application/json", BACKUP_FILE_NAME)
                ?: run {
                    val error = "Could not create $BACKUP_FILE_NAME in backup folder"
                    settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                    return Result.failure(IOException(error))
                }

            val entities = libraryRepository.getAllStationEntities()
            val versionName = systemFacade.getAppVersionName()
            val exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

            val backup = LibraryBackup(
                schemaVersion = 2,
                exportedAt = exportedAt,
                appVersion = versionName,
                stations = entities.map { it.toBackupStation() }
            )
            val json = Gson().toJson(backup)
            val jsonBytes = json.toByteArray(Charsets.UTF_8)

            context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { stream ->
                stream.write(jsonBytes)
                stream.flush()
            } ?: run {
                val error = "Could not open $BACKUP_FILE_NAME for writing"
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(IOException(error))
            }

            val now = System.currentTimeMillis()
            settingsRepository.setLastAutoBackupResult(now, AutoBackupStatus.SUCCESS, null)
            Log.d(TAG, "Auto-backup successful: ${entities.size} stations written to $BACKUP_FILE_NAME")
            return Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Auto-backup failed with exception", e)
            val now = System.currentTimeMillis()
            val msg = formatErrorMessage(e)
            settingsRepository.setLastAutoBackupResult(now, AutoBackupStatus.FAILED, msg)
            return Result.failure(e)
        }
    }

    override suspend fun performRestore(): Result<Int> = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            performRestoreInternal()
        }
    }

    private suspend fun performRestoreInternal(): Result<Int> {
        try {
            val prefs = settingsRepository.appPreferencesFlow.first()
            val uriStr = prefs.autoBackupTreeUri
            if (uriStr.isNullOrBlank()) {
                return Result.failure(IllegalStateException("No backup folder configured"))
            }

            val treeUri = Uri.parse(uriStr)
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && (it.isReadPermission || it.isWritePermission)
            }
            if (!hasPermission) {
                return Result.failure(SecurityException("Storage permission revoked. Please re-select backup folder."))
            }

            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (treeDoc == null || !treeDoc.exists() || !treeDoc.isDirectory) {
                return Result.failure(FileNotFoundException("Backup folder no longer exists. Please re-select a folder."))
            }

            val file = treeDoc.findFile(BACKUP_FILE_NAME)
                ?: return Result.failure(FileNotFoundException("$BACKUP_FILE_NAME not found in backup folder"))

            val json = try {
                context.contentResolver.openInputStream(file.uri)?.bufferedReader(Charsets.UTF_8)?.readText()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            } ?: return Result.failure(IOException("Could not open $BACKUP_FILE_NAME for reading"))

            if (json.isBlank()) {
                return Result.failure(IllegalArgumentException("$BACKUP_FILE_NAME is empty"))
            }

            val backup: LibraryBackup? = try {
                Gson().fromJson(json, LibraryBackup::class.java)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return Result.failure(MalformedBackupException("Backup file ($BACKUP_FILE_NAME) is corrupted or contains invalid JSON", e))
            }

            val rawStations = backup?.stations
                ?: return Result.failure(MalformedBackupException("$BACKUP_FILE_NAME contains no stations or has invalid format"))

            val validStations = rawStations
                .filter { !it.stationUuid.isNullOrBlank() && !it.name.isNullOrBlank() && !it.url.isNullOrBlank() }
                .distinctBy { it.stationUuid }

            if (validStations.isEmpty() && rawStations.isNotEmpty()) {
                return Result.failure(MalformedBackupException("Backup file contains invalid station entries"))
            }

            val strategy = prefs.conflictStrategy
            var totalImportedOrUpdated = 0

            for (backupStation in validStations) {
                try {
                    val entity = backupStation.toStationEntity()
                    val existing = libraryRepository.getEntityById(entity.stationUuid)
                    when {
                        existing == null -> {
                            libraryRepository.insertEntity(entity)
                            totalImportedOrUpdated++
                        }
                        strategy == ConflictStrategy.OVERWRITE -> {
                            libraryRepository.insertEntity(entity)
                            totalImportedOrUpdated++
                        }
                        strategy == ConflictStrategy.KEEP_NEWER -> {
                            if ((entity.addedAt ?: 0L) > (existing.addedAt ?: 0L)) {
                                libraryRepository.insertEntity(entity)
                                totalImportedOrUpdated++
                            }
                        }
                        else -> {
                            // SKIP
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Failed to restore station '${backupStation.name}' (${backupStation.stationUuid})", e)
                }
            }

            Log.d(TAG, "Auto-backup restore completed: $totalImportedOrUpdated stations imported/updated")
            return Result.success(totalImportedOrUpdated)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Restore from auto-backup failed", e)
            return Result.failure(e)
        }
    }

    override suspend fun performSmartSync(): Result<String> = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            performSmartSyncInternal()
        }
    }

    private suspend fun performSmartSyncInternal(): Result<String> {
        try {
            val prefs = settingsRepository.appPreferencesFlow.first()
            val uriStr = prefs.autoBackupTreeUri
            if (uriStr.isNullOrBlank()) {
                val error = "No backup folder configured"
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(IllegalStateException(error))
            }

            val treeUri = Uri.parse(uriStr)
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isWritePermission
            }
            if (!hasPermission) {
                val error = "Storage permission revoked. Please re-select backup folder."
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(SecurityException(error))
            }

            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (treeDoc == null || !treeDoc.exists() || !treeDoc.isDirectory) {
                val error = "Backup folder no longer exists. Please re-select a folder."
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(FileNotFoundException(error))
            }

            if (!treeDoc.canWrite()) {
                val error = "Cannot write to selected backup folder. Permission denied."
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(SecurityException(error))
            }

            val targetFile = treeDoc.findFile(BACKUP_FILE_NAME)
            val localEntities = libraryRepository.getAllStationEntities()

            if (targetFile == null) {
                // stations.json does not exist yet -> initial backup
                val backupResult = performBackupInternal()
                return backupResult.map { "Backup created: ${localEntities.size} stations saved" }
            }

            // stations.json exists -> read it
            val json = try {
                context.contentResolver.openInputStream(targetFile.uri)?.bufferedReader(Charsets.UTF_8)?.readText()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            } ?: run {
                val error = "Could not read $BACKUP_FILE_NAME"
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(IOException(error))
            }

            if (json.isBlank()) {
                // File exists but is empty -> write local entities
                val backupResult = performBackupInternal()
                return backupResult.map { "Backup created: ${localEntities.size} stations saved" }
            }

            val backup: LibraryBackup? = try {
                Gson().fromJson(json, LibraryBackup::class.java)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val error = "Backup file ($BACKUP_FILE_NAME) is corrupted or contains invalid JSON"
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(MalformedBackupException(error, e))
            }

            val rawStations = backup?.stations ?: emptyList()
            val remoteStations = rawStations
                .filter { !it.stationUuid.isNullOrBlank() && !it.name.isNullOrBlank() && !it.url.isNullOrBlank() }
                .distinctBy { it.stationUuid }

            if (localEntities.isEmpty() && remoteStations.isNotEmpty()) {
                // Empty local library -> restore all
                val restoreResult = performRestoreInternal()
                return restoreResult.map { count -> "Restored $count stations from backup" }
            }

            if (localEntities.isEmpty() && remoteStations.isEmpty()) {
                return Result.success("Sync complete: Library is up to date")
            }

            // Both local and remote exist (or remote is empty while local has stations) -> merge with conflict strategy
            val strategy = prefs.conflictStrategy
            var totalImportedOrUpdated = 0

            for (backupStation in remoteStations) {
                try {
                    val entity = backupStation.toStationEntity()
                    val existing = libraryRepository.getEntityById(entity.stationUuid)
                    when {
                        existing == null -> {
                            libraryRepository.insertEntity(entity)
                            totalImportedOrUpdated++
                        }
                        strategy == ConflictStrategy.OVERWRITE -> {
                            libraryRepository.insertEntity(entity)
                            totalImportedOrUpdated++
                        }
                        strategy == ConflictStrategy.KEEP_NEWER -> {
                            if ((entity.addedAt ?: 0L) > (existing.addedAt ?: 0L)) {
                                libraryRepository.insertEntity(entity)
                                totalImportedOrUpdated++
                            }
                        }
                        else -> {
                            // SKIP
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Failed to merge station '${backupStation.name}' (${backupStation.stationUuid})", e)
                }
            }

            // Write back consolidated library to stations.json
            val backupResult = performBackupInternal()
            if (backupResult.isFailure) {
                return Result.failure(backupResult.exceptionOrNull()!!)
            }

            val message = if (totalImportedOrUpdated > 0) {
                "Sync complete: $totalImportedOrUpdated stations restored/updated"
            } else {
                "Sync complete: Library is up to date"
            }
            return Result.success(message)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Smart sync failed", e)
            val now = System.currentTimeMillis()
            val msg = formatErrorMessage(e)
            settingsRepository.setLastAutoBackupResult(now, AutoBackupStatus.FAILED, msg)
            return Result.failure(e)
        }
    }

    private fun formatErrorMessage(e: Throwable): String {
        return when (e) {
            is MalformedBackupException -> "Backup file ($BACKUP_FILE_NAME) is corrupted or contains invalid JSON"
            is JsonSyntaxException -> "Backup file ($BACKUP_FILE_NAME) is corrupted or contains invalid JSON"
            is SecurityException -> e.localizedMessage ?: "Storage permission denied"
            is FileNotFoundException -> e.localizedMessage ?: "Folder or backup file not found"
            is IOException -> e.localizedMessage ?: "Storage read/write error"
            else -> e.localizedMessage ?: "Unknown error"
        }
    }
}
