package com.armanmaurya.internetradio.core.system

import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.OutputStream

interface FileSystemFacade {
    suspend fun createBackupFile(dirUri: Uri, fileName: String): OutputStream?
    
    suspend fun createAudioRecordingFile(folderName: String, fileName: String, mimeType: String): Pair<Uri, ParcelFileDescriptor>?
    suspend fun finalizeAudioRecordingFile(fileUri: Uri, newFileName: String)
    
    suspend fun deleteAudioRecording(fileUri: Uri): Boolean
    
    suspend fun deleteAudioRecordingFolder(folderName: String): Boolean
    
    suspend fun openOutputStream(fileUri: Uri): OutputStream?
    suspend fun openInputStream(fileUri: Uri): java.io.InputStream?
}
