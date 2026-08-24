package com.armanmaurya.internetradio.player

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.armanmaurya.internetradio.data.model.RadioStation
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingFileHelper {
    class OutputSink(
        val outputStream: OutputStream, 
        private val pfd: ParcelFileDescriptor?,
        private val finalizeAction: (() -> Unit)? = null
    ) : java.io.Closeable {
        override fun close() {
            try { outputStream.close() } catch (e: Exception) {}
            try { pfd?.close() } catch (e: Exception) {}
            try { finalizeAction?.invoke() } catch (e: Exception) {}
        }
    }

    fun openOutputSink(station: RadioStation, extension: String, context: Context): OutputSink {
        val safeStationName = station.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val timestamp = SimpleDateFormat("d MMMM yyyy hh-mm a", Locale.getDefault()).format(Date())
        val fileName = "$safeStationName $timestamp.$extension"
        val folderName = "InternetRadio/$safeStationName"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val partFileName = "$fileName.part"
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, partFileName)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeTypeFor(extension))
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$folderName")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
            ) ?: throw IllegalStateException("Failed to create MediaStore URI")
            
            val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
                ?: throw IllegalStateException("Failed to open file descriptor")
                
            val finalizeAction: () -> Unit = {
                val updateValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, updateValues, null, null)
            }
            OutputSink(FileOutputStream(pfd.fileDescriptor), pfd, finalizeAction)
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                folderName
            ).apply { mkdirs() }
            
            val partFile = File(dir, "$fileName.part")
            val pfd = ParcelFileDescriptor.open(partFile, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE)
            
            val finalizeAction: () -> Unit = {
                val finalFile = File(dir, fileName)
                partFile.renameTo(finalFile)
            }
            OutputSink(FileOutputStream(pfd.fileDescriptor), pfd, finalizeAction)
        }
    }

    private fun mimeTypeFor(ext: String) = when (ext) {
        "mp3" -> "audio/mpeg"
        "aac" -> "audio/aac"
        "ogg" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        else  -> "audio/mpeg"
    }
}
