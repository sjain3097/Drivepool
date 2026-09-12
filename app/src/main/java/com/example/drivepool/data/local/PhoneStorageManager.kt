package com.example.drivepool.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.drivepool.data.model.FileCategory
import com.example.drivepool.data.model.LocalPhoneFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class DeviceStorageInfo(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val commercialCapacityGb: Int = 512
) {
    val usagePercent: Float
        get() = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes * 100).toFloat() else 0f

    val formattedTotal: String
        get() = if (commercialCapacityGb > 0) "$commercialCapacityGb GB" else com.example.drivepool.data.model.DriveNode.formatBytes(totalBytes)

    val formattedUsed: String
        get() = com.example.drivepool.data.model.DriveNode.formatBytes(usedBytes)

    val formattedFree: String
        get() = com.example.drivepool.data.model.DriveNode.formatBytes(freeBytes)
}

class PhoneStorageManager(
    private val context: Context
) {
    private val localFilesList = mutableListOf<LocalPhoneFile>()

    /**
     * Queries physical device storage metrics (StatFs) for actual phone capacity (e.g. 512 GB).
     */
    fun getDeviceStorageInfo(): DeviceStorageInfo {
        return try {
            val path = Environment.getDataDirectory()
            val stat = android.os.StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            val used = maxOf(0L, total - free)

            val gb = 1000L * 1000 * 1000
            val gigabytes = total.toDouble() / gb
            val commercial = when {
                gigabytes > 600 -> 1024
                gigabytes > 300 -> 512
                gigabytes > 150 -> 256
                gigabytes > 80 -> 128
                gigabytes > 40 -> 64
                else -> (total / (1024L * 1024 * 1024)).toInt()
            }

            DeviceStorageInfo(
                totalBytes = total,
                usedBytes = used,
                freeBytes = free,
                commercialCapacityGb = commercial
            )
        } catch (_: Exception) {
            DeviceStorageInfo(
                totalBytes = 512L * 1024 * 1024 * 1024,
                usedBytes = 142L * 1024 * 1024 * 1024,
                freeBytes = 370L * 1024 * 1024 * 1024,
                commercialCapacityGb = 512
            )
        }
    }

    suspend fun getPhoneFiles(): List<LocalPhoneFile> = withContext(Dispatchers.IO) {
        val discoveredFiles = mutableListOf<LocalPhoneFile>()

        // 1. Try querying MediaStore for actual media files on device
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED
            )

            val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            context.contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                var count = 0
                while (cursor.moveToNext() && count < 30) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                    val date = cursor.getLong(dateCol) * 1000L

                    if (size > 0) {
                        discoveredFiles.add(
                            LocalPhoneFile(
                                id = "media_$id",
                                name = name,
                                path = "/sdcard/Download/$name",
                                sizeBytes = size,
                                mimeType = mime,
                                modifiedTime = date
                            )
                        )
                        count++
                    }
                }
            }
        } catch (_: Exception) {
            // MediaStore query fallback
        }

        // Merge discovered files with existing local files (avoiding duplicates)
        val existingNames = localFilesList.map { it.name }.toSet()
        val newDiscovered = discoveredFiles.filterNot { it.name in existingNames }
        localFilesList.addAll(0, newDiscovered)

        localFilesList.toList()
    }

    fun markFileUploaded(fileId: String, targetEmail: String) {
        val index = localFilesList.indexOfFirst { it.id == fileId }
        if (index != -1) {
            val file = localFilesList[index]
            localFilesList[index] = file.copy(
                isUploadedToPool = true,
                uploadedToNodeEmail = targetEmail
            )
        }
    }

    fun deleteLocalFile(fileId: String): Boolean {
        return localFilesList.removeIf { it.id == fileId }
    }

    fun addLocalFileFromPicker(name: String, size: Long, mimeType: String, path: String): LocalPhoneFile {
        val newFile = LocalPhoneFile(
            id = "picker_${UUID.randomUUID().toString().take(8)}",
            name = name,
            path = path,
            sizeBytes = size,
            mimeType = mimeType,
            modifiedTime = System.currentTimeMillis()
        )
        localFilesList.add(0, newFile)
        return newFile
    }

    fun openFileInputStream(file: LocalPhoneFile): java.io.InputStream? {
        return try {
            if (file.path.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(file.path))
            } else {
                val f = File(file.path)
                if (f.exists()) f.inputStream() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
