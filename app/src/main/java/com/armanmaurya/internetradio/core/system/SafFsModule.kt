package com.armanmaurya.internetradio.core.system

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.InputStream
import java.io.OutputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SafFsModule @Inject constructor(
    @ApplicationContext private val context: Context
) : FsModule {

    override fun handlesUri(uri: Uri): Boolean {
        return uri.scheme == "content" && uri.authority != "media"
    }

    override suspend fun createFile(dir: Uri, fileName: String, mimeType: String): Uri? {
        return try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(dir)
            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(dir, docId)
            android.provider.DocumentsContract.createDocument(context.contentResolver, docUri, mimeType, fileName)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun openOutputStream(fileUri: Uri): OutputStream? {
        return context.contentResolver.openOutputStream(fileUri)
    }

    override suspend fun openInputStream(fileUri: Uri): InputStream? {
        return context.contentResolver.openInputStream(fileUri)
    }

    override suspend fun getFileDescriptor(fileUri: Uri, mode: String): ParcelFileDescriptor? {
        return context.contentResolver.openFileDescriptor(fileUri, mode)
    }

    override suspend fun deleteFile(fileUri: Uri): Boolean {
        return try {
            android.provider.DocumentsContract.deleteDocument(context.contentResolver, fileUri)
        } catch (e: Exception) {
            false
        }
    }
}
