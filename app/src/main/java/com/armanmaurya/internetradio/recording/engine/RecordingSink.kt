package com.armanmaurya.internetradio.recording.engine

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.armanmaurya.internetradio.core.system.FileSystemFacade
import java.io.Closeable
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class RecordingSink(
    private val fileSystemFacade: FileSystemFacade,
    private val title: String
) : Closeable {

    var outputStream: OutputStream? = null
        private set
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var finalFileName: String? = null

    suspend fun open(ext: String): OutputStream? {
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val timestamp = SimpleDateFormat("d MMMM yyyy hh-mm a", Locale.getDefault()).format(Date())
        val fileName = "$safeTitle $timestamp.$ext"
        val partFileName = "$fileName.part"
        val folderName = "InternetRadio/$safeTitle"
        val mimeType = when (ext) {
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            else  -> "audio/mpeg"
        }

        val result = fileSystemFacade.createAudioRecordingFile(folderName, partFileName, mimeType) ?: return null
        uri = result.first
        pfd = result.second
        finalFileName = fileName
        outputStream = FileOutputStream(result.second.fileDescriptor)
        return outputStream
    }

    suspend fun finalizeFile() {
        try { outputStream?.flush() } catch (_: Exception) {}
        close()
        val targetUri = uri
        val targetName = finalFileName
        if (targetUri != null && targetName != null) {
            try {
                fileSystemFacade.finalizeAudioRecordingFile(targetUri, targetName)
            } catch (_: Exception) {}
        }
    }

    override fun close() {
        try { outputStream?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
    }
}
