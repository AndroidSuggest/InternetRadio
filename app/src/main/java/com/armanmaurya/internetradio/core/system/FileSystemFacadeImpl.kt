package com.armanmaurya.internetradio.core.system

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class FileSystemFacadeImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safFsModule: SafFsModule,
    private val mediaStoreFsModule: MediaStoreFsModule
) : FileSystemFacade {

    override suspend fun createBackupFile(dirUri: Uri, fileName: String): OutputStream? {
        val fileUri = safFsModule.createFile(dirUri, fileName, "application/json") ?: return null
        return safFsModule.openOutputStream(fileUri)
    }

    override suspend fun createAudioRecordingFile(folderName: String, fileName: String, mimeType: String): Pair<Uri, ParcelFileDescriptor>? {
        // We pass the folderName as a query parameter in the dirUri for MediaStoreFsModule to use as relative path
        val dirUri = Uri.Builder().scheme("relative").authority("folder").appendQueryParameter("path", folderName).build()
        val fileUri = mediaStoreFsModule.createFile(dirUri, fileName, mimeType) ?: return null
        val pfd = mediaStoreFsModule.getFileDescriptor(fileUri, "rw") ?: return null
        return Pair(fileUri, pfd)
    }
    
    override suspend fun finalizeAudioRecordingFile(fileUri: Uri, newFileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && fileUri.scheme == "content") {
            val updateValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, newFileName)
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            try {
                context.contentResolver.update(fileUri, updateValues, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (fileUri.scheme == "file") {
            val partFile = File(fileUri.path!!)
            if (partFile.exists()) {
                val finalFile = File(partFile.parentFile, newFileName)
                partFile.renameTo(finalFile)
            }
        }
    }

    override suspend fun deleteAudioRecording(fileUri: Uri): Boolean {
        // Delegate to the correct module based on URI scheme/authority
        return if (safFsModule.handlesUri(fileUri)) {
            safFsModule.deleteFile(fileUri)
        } else {
            mediaStoreFsModule.deleteFile(fileUri)
        }
    }
    
    override suspend fun deleteAudioRecordingFolder(folderName: String): Boolean {
        // Pre-Q (File API) deletion logic for folders, since MediaStore doesn't natively "delete folders"
        val folder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "InternetRadio/$folderName"
        )
        if (folder.exists() && folder.isDirectory) {
            return folder.deleteRecursively()
        }
        return false
    }

    override suspend fun openOutputStream(fileUri: Uri): OutputStream? {
        return if (safFsModule.handlesUri(fileUri)) {
            safFsModule.openOutputStream(fileUri)
        } else {
            mediaStoreFsModule.openOutputStream(fileUri)
        }
    }

    override suspend fun openInputStream(fileUri: Uri): java.io.InputStream? {
        return if (safFsModule.handlesUri(fileUri)) {
            safFsModule.openInputStream(fileUri)
        } else {
            mediaStoreFsModule.openInputStream(fileUri)
        }
    }
}
