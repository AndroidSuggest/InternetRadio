package com.armanmaurya.internetradio.domain.model

import android.net.Uri
import java.io.File

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
