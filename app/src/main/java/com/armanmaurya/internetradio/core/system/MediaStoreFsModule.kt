package com.armanmaurya.internetradio.core.system

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MediaStoreFsModule @Inject constructor(
    @ApplicationContext private val context: Context
) : FsModule {

    override fun handlesUri(uri: Uri): Boolean {
        return uri.scheme == "content" && uri.authority == "media" || uri.scheme == "file"
    }

    override suspend fun createFile(dir: Uri, fileName: String, mimeType: String): Uri? {
        val folderName = "InternetRadio"
        val relativePath = dir.getQueryParameter("path")?.takeIf { it.isNotBlank() } ?: folderName
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$relativePath")
            }
            return context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        } else {
            // Legacy file creation
            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                relativePath
            )
            targetDir.mkdirs()
            val targetFile = File(targetDir, fileName)
            targetFile.createNewFile()
            return Uri.fromFile(targetFile)
        }
    }

    override suspend fun openOutputStream(fileUri: Uri): OutputStream? {
        if (fileUri.scheme == "file") {
            return FileOutputStream(fileUri.path)
        }
        return context.contentResolver.openOutputStream(fileUri)
    }

    override suspend fun openInputStream(fileUri: Uri): InputStream? {
        if (fileUri.scheme == "file") {
            return FileInputStream(fileUri.path)
        }
        return context.contentResolver.openInputStream(fileUri)
    }

    override suspend fun getFileDescriptor(fileUri: Uri, mode: String): ParcelFileDescriptor? {
        if (fileUri.scheme == "file") {
            val fileMode = if (mode.contains("w")) {
                ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
            } else {
                ParcelFileDescriptor.MODE_READ_ONLY
            }
            return ParcelFileDescriptor.open(File(fileUri.path!!), fileMode)
        }
        return context.contentResolver.openFileDescriptor(fileUri, mode)
    }

    override suspend fun deleteFile(fileUri: Uri): Boolean {
        if (fileUri.scheme == "file") {
            val file = File(fileUri.path!!)
            val deleted = file.delete()
            if (deleted) {
                // Try to clean up empty parent directory
                val parent = file.parentFile
                if (parent != null && parent.isDirectory && parent.listFiles()?.isEmpty() == true) {
                    parent.delete()
                }
            }
            return deleted
        }
        
        val deletedRows = try {
            context.contentResolver.delete(fileUri, null, null)
        } catch (e: Exception) {
            0
        }
        return deletedRows > 0
    }
}
