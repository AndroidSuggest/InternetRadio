package com.armanmaurya.internetradio.ui.shared.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.model.AppPreferences
import com.armanmaurya.internetradio.data.model.ConflictStrategy
import com.armanmaurya.internetradio.data.model.LibraryBackup
import com.armanmaurya.internetradio.data.model.toBackupStation
import com.armanmaurya.internetradio.data.repository.LibraryRepository
import com.armanmaurya.internetradio.data.repository.SettingsRepository
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    val uiState: StateFlow<AppPreferences> = settingsRepository.appPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppPreferences()
        )

    private val _backupResult = Channel<String>(Channel.BUFFERED)
    val backupResult = _backupResult.receiveAsFlow()

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(theme)
        }
    }

    fun setDynamicTheme(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setPureBlack(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPureBlack(enabled)
        }
    }

    fun setAutoRouteToBrowseOnSearch(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoRouteToBrowseOnSearch(enabled)
        }
    }

    fun setAutoPlayOnStart(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoPlayOnStart(enabled)
        }
    }

    fun setDisableUpdateCheck(disabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDisableUpdateCheck(disabled)
        }
    }

    fun setStopOnAudioBecomingNoisy(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStopOnAudioBecomingNoisy(enabled)
        }
    }

    fun setAppLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.setAppLanguage(language)
            val localeList = if (language == "System") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language)
            }
            AppCompatDelegate.setApplicationLocales(localeList)
        }
    }

    fun setTrackHistoryLimit(limit: Int) {
        viewModelScope.launch {
            settingsRepository.setTrackHistoryLimit(limit)
        }
    }

    fun setDefaultTab(tabIndex: Int) {
        viewModelScope.launch {
            settingsRepository.setDefaultTab(tabIndex)
        }
    }

    fun setMaxRetryDuration(durationInMillis: Long) {
        viewModelScope.launch {
            settingsRepository.setMaxRetryDuration(durationInMillis)
        }
    }

    fun setConflictStrategy(strategy: ConflictStrategy) {
        viewModelScope.launch {
            settingsRepository.setConflictStrategy(strategy)
        }
    }

    fun setStartOfWeek(startOfWeek: com.armanmaurya.internetradio.data.model.StartOfWeek) {
        viewModelScope.launch {
            settingsRepository.setStartOfWeek(startOfWeek)
        }
    }

    fun setShowCoverArtInNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowCoverArtInNotification(enabled)
        }
    }

    fun exportLibrary(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entities = libraryRepository.getAllStationEntities()
                Log.d(TAG, "Exporting ${entities.size} stations to $uri")

                val versionName = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    Log.w(TAG, "Could not get version name", e)
                    "unknown"
                }

                // Use SimpleDateFormat for API 24 compatibility (Instant.now() requires API 26)
                val exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .format(Date())

                val backup = LibraryBackup(
                    exportedAt = exportedAt,
                    appVersion = versionName ?: "unknown",
                    stations = entities.map { it.toBackupStation() }
                )
                val json = Gson().toJson(backup)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray())
                } ?: run {
                    Log.e(TAG, "Export failed: output stream was null for uri=$uri")
                    _backupResult.send("Export failed: could not open file for writing")
                    return@launch
                }

                Log.d(TAG, "Export successful: ${entities.size} stations written")
                _backupResult.send("Exported ${entities.size} station(s) successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Export failed with exception", e)
                _backupResult.send("Export failed: ${e.localizedMessage}")
            }
        }
    }

    fun importLibraries(context: Context, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            var totalImported = 0
            var totalUpdated = 0
            var totalSkipped = 0
            var failedFiles = 0

            val strategy = uiState.value.conflictStrategy
            Log.d(TAG, "Using conflict strategy: $strategy")

            for (uri in uris) {
                try {
                    Log.d(TAG, "Starting import from $uri")

                    val json = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.readText()
                        ?: run {
                            Log.e(TAG, "Import failed: could not open input stream for uri=$uri")
                            failedFiles++
                            continue
                        }

                    val backup: LibraryBackup? = try {
                        Gson().fromJson(json, LibraryBackup::class.java)
                    } catch (e: JsonSyntaxException) {
                        Log.e(TAG, "Import failed: invalid JSON format", e)
                        failedFiles++
                        continue
                    }

                    if (backup == null || backup.stations == null) {
                        Log.e(TAG, "Import failed: File empty or stations missing")
                        failedFiles++
                        continue
                    }

                    backup.stations.forEach { backupStation ->
                        val entity = backupStation.toLibraryStationEntity()
                        try {
                            val existing = libraryRepository.getEntityById(entity.stationUuid)
                            when {
                                existing == null -> {
                                    libraryRepository.insertEntity(entity)
                                    totalImported++
                                }
                                strategy == ConflictStrategy.OVERWRITE -> {
                                    libraryRepository.insertEntity(entity)
                                    totalUpdated++
                                }
                                strategy == ConflictStrategy.KEEP_NEWER -> {
                                    if (entity.addedAt > existing.addedAt) {
                                        libraryRepository.insertEntity(entity)
                                        totalUpdated++
                                    } else {
                                        totalSkipped++
                                    }
                                }
                                else -> {
                                    totalSkipped++
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process station '${entity.name}' (${entity.stationUuid})", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Import failed with unexpected exception for uri=$uri", e)
                    failedFiles++
                }
            }

            val parts = buildList {
                if (totalImported > 0) add("Imported $totalImported")
                if (totalUpdated > 0) add("Updated $totalUpdated")
                if (totalSkipped > 0) add("Skipped $totalSkipped already existing")
                if (failedFiles > 0) add("Failed to read $failedFiles file(s)")
                if (isEmpty()) add("No changes — all stations already exist")
            }
            val resultMessage = parts.joinToString(", ")
            Log.d(TAG, "Import complete: $resultMessage")
            _backupResult.send(resultMessage)
        }
    }
}