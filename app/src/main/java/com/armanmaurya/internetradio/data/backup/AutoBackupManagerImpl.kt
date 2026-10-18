package com.armanmaurya.internetradio.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.armanmaurya.internetradio.R
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
                val error = context.getString(R.string.settings_backup_error_no_folder)
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(IllegalStateException(error))
            }

            val treeUri = Uri.parse(uriStr)
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isWritePermission
            }
            if (!hasPermission) {
                val error = context.getString(R.string.settings_backup_error_permission_revoked)
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(SecurityException(error))
            }

            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (treeDoc == null || !treeDoc.exists() || !treeDoc.isDirectory) {
                val error = context.getString(R.string.settings_backup_error_folder_deleted)
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(FileNotFoundException(error))
            }

            if (!treeDoc.canWrite()) {
                val error = context.getString(R.string.settings_backup_error_cannot_write)
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(SecurityException(error))
            }

            val targetFile = treeDoc.findFile(BACKUP_FILE_NAME)
                ?: treeDoc.createFile("application/json", BACKUP_FILE_NAME)
                ?: run {
                    val error = context.getString(R.string.settings_backup_error_create_file, BACKUP_FILE_NAME)
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
                val error = context.getString(R.string.settings_backup_error_open_write, BACKUP_FILE_NAME)
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
                return Result.failure(IllegalStateException(context.getString(R.string.settings_backup_error_no_folder)))
            }

            val treeUri = Uri.parse(uriStr)
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && (it.isReadPermission || it.isWritePermission)
            }
            if (!hasPermission) {
                return Result.failure(SecurityException(context.getString(R.string.settings_backup_error_permission_revoked)))
            }

            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (treeDoc == null || !treeDoc.exists() || !treeDoc.isDirectory) {
                return Result.failure(FileNotFoundException(context.getString(R.string.settings_backup_error_folder_deleted)))
            }

            val file = treeDoc.findFile(BACKUP_FILE_NAME)
                ?: return Result.failure(FileNotFoundException(context.getString(R.string.settings_backup_error_file_not_found, BACKUP_FILE_NAME)))

            val json = try {
                context.contentResolver.openInputStream(file.uri)?.bufferedReader(Charsets.UTF_8)?.readText()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            } ?: return Result.failure(IOException(context.getString(R.string.settings_backup_error_open_read, BACKUP_FILE_NAME)))

            if (json.isBlank()) {
                return Result.failure(IllegalArgumentException(context.getString(R.string.settings_backup_error_file_empty, BACKUP_FILE_NAME)))
            }

            val backup: LibraryBackup? = try {
                Gson().fromJson(json, LibraryBackup::class.java)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return Result.failure(MalformedBackupException(context.getString(R.string.settings_backup_error_corrupted_json, BACKUP_FILE_NAME), e))
            }

            val rawStations = backup?.stations
                ?: return Result.failure(MalformedBackupException(context.getString(R.string.settings_backup_error_no_stations, BACKUP_FILE_NAME)))

            val validStations = rawStations
                .filter { !it.stationUuid.isNullOrBlank() && !it.name.isNullOrBlank() && !it.url.isNullOrBlank() }
                .distinctBy { it.stationUuid }

            if (validStations.isEmpty() && rawStations.isNotEmpty()) {
                return Result.failure(MalformedBackupException(context.getString(R.string.settings_backup_error_invalid_stations)))
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
                val error = context.getString(R.string.settings_backup_error_no_folder)
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(IllegalStateException(error))
            }

            val treeUri = Uri.parse(uriStr)
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isWritePermission
            }
            if (!hasPermission) {
                val error = context.getString(R.string.settings_backup_error_permission_revoked)
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(SecurityException(error))
            }

            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (treeDoc == null || !treeDoc.exists() || !treeDoc.isDirectory) {
                val error = context.getString(R.string.settings_backup_error_folder_deleted)
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(FileNotFoundException(error))
            }

            if (!treeDoc.canWrite()) {
                val error = context.getString(R.string.settings_backup_error_cannot_write)
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(SecurityException(error))
            }

            val targetFile = treeDoc.findFile(BACKUP_FILE_NAME)
            val localEntities = libraryRepository.getAllStationEntities()

            if (targetFile == null) {
                // stations.json does not exist yet -> initial backup
                val backupResult = performBackupInternal()
                return backupResult.map {
                    context.resources.getQuantityString(R.plurals.settings_sync_backup_created, localEntities.size, localEntities.size)
                }
            }

            // stations.json exists -> read it
            val json = try {
                context.contentResolver.openInputStream(targetFile.uri)?.bufferedReader(Charsets.UTF_8)?.readText()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            } ?: run {
                val error = context.getString(R.string.settings_backup_error_read, BACKUP_FILE_NAME)
                settingsRepository.setLastAutoBackupResult(System.currentTimeMillis(), AutoBackupStatus.FAILED, error)
                return Result.failure(IOException(error))
            }

            if (json.isBlank()) {
                // File exists but is empty -> write local entities
                val backupResult = performBackupInternal()
                return backupResult.map {
                    context.resources.getQuantityString(R.plurals.settings_sync_backup_created, localEntities.size, localEntities.size)
                }
            }

            val backup: LibraryBackup? = try {
                Gson().fromJson(json, LibraryBackup::class.java)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val error = context.getString(R.string.settings_backup_error_corrupted_json, BACKUP_FILE_NAME)
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
                return restoreResult.map { count ->
                    context.resources.getQuantityString(R.plurals.settings_sync_restored, count, count)
                }
            }

            if (localEntities.isEmpty() && remoteStations.isEmpty()) {
                return Result.success(context.getString(R.string.settings_sync_up_to_date))
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
                context.resources.getQuantityString(
                    R.plurals.settings_sync_success_merged,
                    totalImportedOrUpdated,
                    totalImportedOrUpdated
                )
            } else {
                context.getString(R.string.settings_sync_up_to_date)
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
            is MalformedBackupException -> context.getString(R.string.settings_backup_error_corrupted_json, BACKUP_FILE_NAME)
            is JsonSyntaxException -> context.getString(R.string.settings_backup_error_corrupted_json, BACKUP_FILE_NAME)
            is SecurityException -> e.localizedMessage ?: context.getString(R.string.settings_backup_error_permission_denied)
            is FileNotFoundException -> e.localizedMessage ?: context.getString(R.string.settings_backup_error_not_found)
            is IOException -> e.localizedMessage ?: context.getString(R.string.settings_backup_error_io)
            else -> e.localizedMessage ?: context.getString(R.string.error_unknown)
        }
    }
}
