package com.armanmaurya.internetradio.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RecordingFolder(
    val stationName: String,
    val recordings: List<RecordingFile>
)

data class RecordingFile(
    val fileName: String,
    val file: File,
    val uri: Uri,
    val lastModified: Long,
    val sizeBytes: Long
)

@Singleton
class RecordingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getRecordingFolders(): List<RecordingFolder> = withContext(Dispatchers.IO) {
        val rootDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "InternetRadio")
        if (!rootDir.exists() || !rootDir.isDirectory) return@withContext emptyList()

        val folders = mutableListOf<RecordingFolder>()
        val stationDirs = rootDir.listFiles { file -> file.isDirectory } ?: emptyArray()
        
        for (dir in stationDirs) {
            val files = dir.listFiles { file -> file.isFile && (file.name.endsWith(".mp3") || file.name.endsWith(".aac") || file.name.endsWith(".m4a")) } ?: emptyArray()
            if (files.isNotEmpty()) {
                val recordings = files.map {
                    RecordingFile(
                        fileName = it.name,
                        file = it,
                        uri = Uri.fromFile(it),
                        lastModified = it.lastModified(),
                        sizeBytes = it.length()
                    )
                }.sortedByDescending { it.lastModified }
                
                folders.add(RecordingFolder(stationName = dir.name, recordings = recordings))
            }
        }
        
        folders.sortedBy { it.stationName }
    }
    
    suspend fun getRecordingsForStation(stationName: String): List<RecordingFile> = withContext(Dispatchers.IO) {
        val safeStationName = stationName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val stationDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "InternetRadio/$safeStationName")
        if (!stationDir.exists() || !stationDir.isDirectory) return@withContext emptyList()
        
        val files = stationDir.listFiles { file -> file.isFile && (file.name.endsWith(".mp3") || file.name.endsWith(".aac") || file.name.endsWith(".m4a")) } ?: emptyArray()
        files.map {
            RecordingFile(
                fileName = it.name,
                file = it,
                uri = Uri.fromFile(it),
                lastModified = it.lastModified(),
                sizeBytes = it.length()
            )
        }.sortedByDescending { it.lastModified }
    }

    private val _recordingsChangedEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recordingsChangedEvent: kotlinx.coroutines.flow.SharedFlow<Unit> = _recordingsChangedEvent.asSharedFlow()

    fun notifyRecordingsChanged() {
        _recordingsChangedEvent.tryEmit(Unit)
    }

    suspend fun deleteRecording(recording: RecordingFile): Boolean = withContext(Dispatchers.IO) {
        val deleted = recording.file.delete()
        if (deleted) {
            val parent = recording.file.parentFile
            if (parent != null && parent.isDirectory) {
                val files = parent.listFiles()
                if (files != null && files.isEmpty()) {
                    parent.delete()
                }
            }
            notifyRecordingsChanged()
        }
        deleted
    }
}
