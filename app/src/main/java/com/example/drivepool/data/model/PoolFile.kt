package com.example.drivepool.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileCategory {
    ALL,
    DOCUMENTS,
    IMAGES,
    VIDEOS,
    AUDIO,
    ARCHIVES,
    OTHER;

    companion object {
        fun fromMimeOrExtension(name: String, mimeType: String): FileCategory {
            val lowerName = name.lowercase(Locale.ROOT)
            val lowerMime = mimeType.lowercase(Locale.ROOT)

            return when {
                lowerMime.startsWith("image/") ||
                        lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
                        lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif") ||
                        lowerName.endsWith(".webp") || lowerName.endsWith(".svg") -> IMAGES

                lowerMime.startsWith("video/") ||
                        lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
                        lowerName.endsWith(".avi") || lowerName.endsWith(".mov") -> VIDEOS

                lowerMime.startsWith("audio/") ||
                        lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ||
                        lowerName.endsWith(".m4a") || lowerName.endsWith(".flac") -> AUDIO

                lowerMime.contains("pdf") || lowerMime.contains("word") ||
                        lowerMime.contains("document") || lowerMime.contains("presentation") ||
                        lowerMime.contains("spreadsheet") || lowerMime.contains("text") ||
                        lowerName.endsWith(".pdf") || lowerName.endsWith(".doc") ||
                        lowerName.endsWith(".docx") || lowerName.endsWith(".txt") ||
                        lowerName.endsWith(".xlsx") || lowerName.endsWith(".pptx") ||
                        lowerName.endsWith(".md") -> DOCUMENTS

                lowerMime.contains("zip") || lowerMime.contains("compressed") ||
                        lowerName.endsWith(".zip") || lowerName.endsWith(".rar") ||
                        lowerName.endsWith(".tar") || lowerName.endsWith(".gz") ||
                        lowerName.endsWith(".7z") -> ARCHIVES

                else -> OTHER
            }
        }
    }
}

data class PoolFile(
    val id: String,
    val name: String,
    val virtualPath: String = "/",
    val sizeBytes: Long,
    val mimeType: String = "application/octet-stream",
    val modifiedTime: Long = System.currentTimeMillis(),
    val physicalNodeId: String,
    val physicalNodeEmail: String,
    val remoteDriveFileId: String,
    val remoteDrivePath: String = "/DrivePool/$name",
    val checksumSha256: String = "",
    val isStarred: Boolean = false,
    val isTrashed: Boolean = false,
    val webViewLink: String = "https://drive.google.com/file/d/$remoteDriveFileId/view"
) {
    val category: FileCategory
        get() = FileCategory.fromMimeOrExtension(name, mimeType)

    val formattedSize: String
        get() = DriveNode.formatBytes(sizeBytes)

    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(modifiedTime))
}
