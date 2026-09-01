package com.armanmaurya.internetradio.core.system

import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.InputStream
import java.io.OutputStream

interface FsModule {
    fun handlesUri(uri: Uri): Boolean
    
    suspend fun createFile(dir: Uri, fileName: String, mimeType: String): Uri?
    suspend fun openOutputStream(fileUri: Uri): OutputStream?
    suspend fun openInputStream(fileUri: Uri): InputStream?
    suspend fun getFileDescriptor(fileUri: Uri, mode: String): ParcelFileDescriptor?
    suspend fun deleteFile(fileUri: Uri): Boolean
}
