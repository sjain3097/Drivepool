package com.example.drivepool.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LocalPhoneFile(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val mimeType: String = "application/octet-stream",
    val modifiedTime: Long = System.currentTimeMillis(),
    val isUploadedToPool: Boolean = false,
    val uploadedToNodeEmail: String? = null
) {
    val category: FileCategory
        get() = FileCategory.fromMimeOrExtension(name, mimeType)

    val formattedSize: String
        get() = DriveNode.formatBytes(sizeBytes)

    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(modifiedTime))
}
