package com.armanmaurya.internetradio.core.media.recorder

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.armanmaurya.internetradio.core.system.FileSystemFacade
import com.armanmaurya.internetradio.core.utils.FormatUtils
import java.io.Closeable
import java.io.FileOutputStream
import java.io.OutputStream

class RecordingSink(
    private val fileSystemFacade: FileSystemFacade,
    private val title: String,
    private val context: Context
) : Closeable {

    var outputStream: OutputStream? = null
        private set
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var finalFileName: String? = null

    suspend fun open(ext: String): OutputStream? {
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val timestamp = FormatUtils.getRecordingTimestamp(context)
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
