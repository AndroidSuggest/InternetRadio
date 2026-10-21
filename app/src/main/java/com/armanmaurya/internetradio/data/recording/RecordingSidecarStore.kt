package com.armanmaurya.internetradio.data.recording

import android.content.Context
import android.os.Environment
import com.armanmaurya.internetradio.domain.model.RecordingTrack
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RecordingSidecarDto(
    val stationName: String = "",
    val fileName: String = "",
    val tracks: List<RecordingTrack> = emptyList()
)

@Singleton
class RecordingSidecarStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val appSidecarRoot: File
        get() = context.getExternalFilesDir("recordings_sidecar")
            ?: File(context.filesDir, "recordings_sidecar")

    private val musicInternetRadioRoot: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "InternetRadio"
        )

    fun saveTracks(
        stationFolderName: String,
        fileName: String,
        tracks: List<RecordingTrack>
    ) {
        if (fileName.isBlank() || tracks.isEmpty()) return
        val safeStation = sanitizeFolderName(stationFolderName)
        val json = serializeSidecar(
            RecordingSidecarDto(
                stationName = stationFolderName,
                fileName = fileName,
                tracks = tracks.sortedBy { it.offsetMs }
            )
        )

        // 1. Always write to app-scoped sidecar directory (guaranteed under Android 11+ Scoped Storage)
        runCatching {
            val dir = File(appSidecarRoot, safeStation)
            dir.mkdirs()
            File(dir, "$fileName.json").writeText(json, Charsets.UTF_8)
        }

        // 2. Also attempt to write sidecar JSON directly next to the audio file in Music/InternetRadio/<station>/
        runCatching {
            val publicStationDir = File(musicInternetRadioRoot, safeStation)
            if (publicStationDir.exists() || publicStationDir.mkdirs()) {
                File(publicStationDir, "$fileName.json").writeText(json, Charsets.UTF_8)
            }
        }
    }

    fun readTracks(audioFile: File, stationName: String = audioFile.parentFile?.name ?: ""): List<RecordingTrack> {
        val adjacentFile = audioFile.parentFile?.let { File(it, "${audioFile.name}.json") }
        if (adjacentFile != null && adjacentFile.isFile && adjacentFile.canRead()) {
            val parsed = runCatching { parseSidecar(adjacentFile.readText(Charsets.UTF_8)) }.getOrNull()
            if (!parsed.isNullOrEmpty()) return parsed
        }

        val safeStation = sanitizeFolderName(stationName)
        val appSidecarFile = File(File(appSidecarRoot, safeStation), "${audioFile.name}.json")
        if (appSidecarFile.isFile && appSidecarFile.canRead()) {
            val parsed = runCatching { parseSidecar(appSidecarFile.readText(Charsets.UTF_8)) }.getOrNull()
            if (!parsed.isNullOrEmpty()) return parsed
        }

        return emptyList()
    }

    fun deleteSidecar(audioFile: File, stationName: String = audioFile.parentFile?.name ?: "") {
        runCatching {
            audioFile.parentFile?.let { parent ->
                val adjacent = File(parent, "${audioFile.name}.json")
                if (adjacent.exists()) adjacent.delete()
            }
        }
        runCatching {
            val safeStation = sanitizeFolderName(stationName)
            val stationDir = File(appSidecarRoot, safeStation)
            val sidecar = File(stationDir, "${audioFile.name}.json")
            if (sidecar.exists()) sidecar.delete()
            if (stationDir.isDirectory && stationDir.listFiles()?.isEmpty() == true) {
                stationDir.delete()
            }
        }
    }

    fun deleteStationSidecars(stationName: String) {
        val safeStation = sanitizeFolderName(stationName)
        runCatching {
            val publicDir = File(musicInternetRadioRoot, safeStation)
            publicDir.listFiles { f -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }
                ?.forEach { it.delete() }
        }
        runCatching {
            val appDir = File(appSidecarRoot, safeStation)
            if (appDir.exists()) appDir.deleteRecursively()
        }
    }

    internal fun serializeSidecar(dto: RecordingSidecarDto): String = gson.toJson(dto)

    internal fun parseSidecar(json: String): List<RecordingTrack> {
        if (json.isBlank()) return emptyList()
        val dto = gson.fromJson(json, RecordingSidecarDto::class.java) ?: return emptyList()
        return dto.tracks.orEmpty().sortedBy { it.offsetMs }
    }

    private fun sanitizeFolderName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
}
