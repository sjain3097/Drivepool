package com.example.drivepool.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.drivepool.data.model.FileCategory
import com.example.drivepool.data.model.LocalPhoneFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class PhoneFolderItem(
    val name: String,
    val path: String,
    val itemCount: Int,
    val lastModified: Long
)

data class FolderContentResult(
    val currentPath: String,
    val parentPath: String?,
    val folders: List<PhoneFolderItem>,
    val files: List<LocalPhoneFile>
)

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

    private fun resolveMediaId(id: String): Long? {
        return when {
            id.startsWith("img_") -> id.removePrefix("img_").toLongOrNull()
            id.startsWith("vid_") -> id.removePrefix("vid_").toLongOrNull()
            id.startsWith("aud_") -> id.removePrefix("aud_").toLongOrNull()
            id.startsWith("doc_") -> id.removePrefix("doc_").toLongOrNull()
            id.startsWith("media_") -> id.removePrefix("media_").toLongOrNull()
            else -> null
        }
    }

    suspend fun getPhoneFiles(): List<LocalPhoneFile> = withContext(Dispatchers.IO) {
        val discoveredFiles = mutableListOf<LocalPhoneFile>()

        // 1. Query Images (Camera, Screenshots, Pictures, Downloads) with high capacity
        queryMediaUri(
            uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            },
            idPrefix = "img_",
            defaultMime = "image/jpeg",
            maxCount = 1000,
            discovered = discoveredFiles
        )

        // 2. Query Videos
        queryMediaUri(
            uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            },
            idPrefix = "vid_",
            defaultMime = "video/mp4",
            maxCount = 300,
            discovered = discoveredFiles
        )

        // 3. Query Audio
        queryMediaUri(
            uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            },
            idPrefix = "aud_",
            defaultMime = "audio/mpeg",
            maxCount = 300,
            discovered = discoveredFiles
        )

        // 4. Query Documents and Downloads
        queryDocumentsAndDownloads(
            maxCount = 500,
            discovered = discoveredFiles
        )

        // 5. Scan direct Downloads directory for any unindexed files
        scanDownloadFolder(discoveredFiles)

        // Clean up any files from localFilesList that no longer physically exist on disk
        localFilesList.removeAll { file ->
            !file.path.startsWith("content://") && !File(file.path).exists()
        }

        // Merge discovered files with existing local files (avoiding duplicates by path and ID)
        val existingPaths = localFilesList.map { it.path }.toSet()
        val existingIds = localFilesList.map { it.id }.toSet()
        val newDiscovered = discoveredFiles.filterNot { it.path in existingPaths || it.id in existingIds }
        localFilesList.addAll(0, newDiscovered)

        // Sort by most recently modified
        localFilesList.sortByDescending { it.modifiedTime }

        localFilesList.toList()
    }

    private fun queryMediaUri(
        uri: Uri,
        idPrefix: String,
        defaultMime: String,
        maxCount: Int,
        discovered: MutableList<LocalPhoneFile>
    ) {
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATA
            )

            context.contentResolver.query(
                uri,
                projection,
                "${MediaStore.MediaColumns.SIZE} > 0",
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

                var count = 0
                while (cursor.moveToNext() && count < maxCount) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: defaultMime
                    val date = cursor.getLong(dateCol) * 1000L
                    val rawPath = if (dataCol != -1) cursor.getString(dataCol) else null

                    if (!rawPath.isNullOrBlank()) {
                        val checkFile = File(rawPath)
                        if (!checkFile.exists()) {
                            try {
                                context.contentResolver.delete(
                                    uri,
                                    "${MediaStore.MediaColumns._ID}=?",
                                    arrayOf(id.toString())
                                )
                            } catch (_: Exception) {}
                            continue
                        }
                    }

                    val resolvedPath = if (!rawPath.isNullOrBlank()) {
                        rawPath
                    } else {
                        ContentUris.withAppendedId(uri, id).toString()
                    }

                    if (size > 0) {
                        discovered.add(
                            LocalPhoneFile(
                                id = "$idPrefix$id",
                                name = name,
                                path = resolvedPath,
                                sizeBytes = size,
                                mimeType = mime,
                                modifiedTime = date
                            )
                        )
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("PhoneStorageManager", "Error querying $uri: ${e.message}")
        }
    }

    private fun queryDocumentsAndDownloads(
        maxCount: Int,
        discovered: MutableList<LocalPhoneFile>
    ) {
        try {
            val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATA
            )

            val selection = "${MediaStore.MediaColumns.SIZE} > 0 AND (" +
                    "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/%' OR " +
                    "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'text/%')"

            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

                var count = 0
                while (cursor.moveToNext() && count < maxCount) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                    val date = cursor.getLong(dateCol) * 1000L
                    val rawPath = if (dataCol != -1) cursor.getString(dataCol) else null

                    if (!rawPath.isNullOrBlank()) {
                        val checkFile = File(rawPath)
                        if (!checkFile.exists()) {
                            try {
                                context.contentResolver.delete(
                                    queryUri,
                                    "${MediaStore.MediaColumns._ID}=?",
                                    arrayOf(id.toString())
                                )
                            } catch (_: Exception) {}
                            continue
                        }
                    }

                    val resolvedPath = if (!rawPath.isNullOrBlank()) {
                        rawPath
                    } else {
                        ContentUris.withAppendedId(queryUri, id).toString()
                    }

                    if (size > 0) {
                        discovered.add(
                            LocalPhoneFile(
                                id = "doc_$id",
                                name = name,
                                path = resolvedPath,
                                sizeBytes = size,
                                mimeType = mime,
                                modifiedTime = date
                            )
                        )
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("PhoneStorageManager", "Error querying documents: ${e.message}")
        }
    }

    private fun scanDownloadFolder(discovered: MutableList<LocalPhoneFile>) {
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && downloadDir.exists() && downloadDir.isDirectory) {
                val existingPaths = discovered.map { it.path }.toSet()
                downloadDir.listFiles()?.take(100)?.forEach { file ->
                    if (file.isFile && file.length() > 0 && !file.name.startsWith(".") && file.absolutePath !in existingPaths) {
                        discovered.add(
                            LocalPhoneFile(
                                id = "dl_${file.name.hashCode()}",
                                name = file.name,
                                path = file.absolutePath,
                                sizeBytes = file.length(),
                                mimeType = getMimeType(file.name),
                                modifiedTime = file.lastModified()
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
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

    /**
     * Physically deletes a local file from disk and MediaStore index.
     */
    fun deletePhysicalFile(path: String, id: String): Boolean {
        var success = false
        try {
            if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                val rows = context.contentResolver.delete(uri, null, null)
                success = rows > 0
            } else {
                val f = File(path)
                if (f.exists()) {
                    val deleted = f.delete()
                    if (deleted) success = true
                } else {
                    success = true // Already gone from disk
                }

                // Clean up MediaStore by absolute path
                try {
                    val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Files.getContentUri("external")
                    }
                    context.contentResolver.delete(
                        queryUri,
                        "${MediaStore.MediaColumns.DATA}=?",
                        arrayOf(path)
                    )
                } catch (_: Exception) {}

                // Clean up MediaStore by media ID if applicable
                if (id.startsWith("media_")) {
                    val mediaId = id.removePrefix("media_").toLongOrNull()
                    if (mediaId != null) {
                        try {
                            val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                            } else {
                                MediaStore.Files.getContentUri("external")
                            }
                            val contentUri = ContentUris.withAppendedId(queryUri, mediaId)
                            context.contentResolver.delete(contentUri, null, null)
                            success = true
                        } catch (_: Exception) {}
                    }
                }

                // Request MediaScanner to update system database immediately
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(path),
                        null,
                        null
                    )
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("PhoneStorageManager", "Error deleting physical file at $path: ${e.message}")
        }
        return success
    }

    fun deleteLocalFile(file: LocalPhoneFile): Boolean {
        val physicalDeleted = deletePhysicalFile(file.path, file.id)
        localFilesList.removeIf { it.id == file.id || it.path == file.path }
        return physicalDeleted || true
    }

    fun deleteLocalFile(fileId: String, extraFiles: List<LocalPhoneFile> = emptyList()): Boolean {
        val file = (localFilesList + extraFiles).find { it.id == fileId }
        return if (file != null) {
            deleteLocalFile(file)
        } else {
            localFilesList.removeIf { it.id == fileId }
        }
    }

    fun deleteLocalFiles(fileIds: Set<String>, extraFiles: List<LocalPhoneFile> = emptyList()): Int {
        val allKnown = (localFilesList + extraFiles).distinctBy { it.id }
        val toDelete = allKnown.filter { it.id in fileIds }
        var count = 0
        val deletedPaths = mutableSetOf<String>()
        val deletedIds = mutableSetOf<String>()

        for (file in toDelete) {
            val success = deletePhysicalFile(file.path, file.id)
            if (success) {
                count++
                deletedPaths.add(file.path)
                deletedIds.add(file.id)
            }
        }

        localFilesList.removeIf { it.id in fileIds || it.id in deletedIds || it.path in deletedPaths }
        return if (count > 0) count else toDelete.size
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
                if (f.exists()) {
                    f.inputStream()
                } else if (file.id.startsWith("media_")) {
                    val mediaId = file.id.removePrefix("media_").toLongOrNull()
                    if (mediaId != null) {
                        val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                        } else {
                            MediaStore.Files.getContentUri("external")
                        }
                        val contentUri = android.content.ContentUris.withAppendedId(queryUri, mediaId)
                        context.contentResolver.openInputStream(contentUri)
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getManageStorageIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    suspend fun listFolderContents(folderPath: String? = null): FolderContentResult = withContext(Dispatchers.IO) {
        val rootDir = Environment.getExternalStorageDirectory()
        val targetDir = if (folderPath.isNullOrBlank()) rootDir else File(folderPath)

        val folders = mutableListOf<PhoneFolderItem>()
        val files = mutableListOf<LocalPhoneFile>()

        val parentPath = if (targetDir.absolutePath != rootDir.absolutePath) {
            targetDir.parentFile?.absolutePath ?: rootDir.absolutePath
        } else {
            null
        }

        try {
            val children = targetDir.listFiles() ?: emptyArray()
            val sortedChildren = children.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            for (child in sortedChildren) {
                if (child.name.startsWith(".")) continue // Skip hidden files/dirs

                if (child.isDirectory) {
                    val count = child.list()?.size ?: 0
                    folders.add(
                        PhoneFolderItem(
                            name = child.name,
                            path = child.absolutePath,
                            itemCount = count,
                            lastModified = child.lastModified()
                        )
                    )
                } else if (child.isFile) {
                    val mime = getMimeType(child.name)
                    files.add(
                        LocalPhoneFile(
                            id = "file_${child.absolutePath.hashCode()}",
                            name = child.name,
                            path = child.absolutePath,
                            sizeBytes = child.length(),
                            mimeType = mime,
                            modifiedTime = child.lastModified()
                        )
                    )
                }
            }
        } catch (_: Exception) { }

        FolderContentResult(
            currentPath = targetDir.absolutePath,
            parentPath = parentPath,
            folders = folders,
            files = files
        )
    }

    fun getFileForSharing(file: LocalPhoneFile): File? {
        return try {
            if (file.path.startsWith("content://")) {
                val cacheDir = File(context.cacheDir, "shared_phone_files").apply { mkdirs() }
                val tempFile = File(cacheDir, "${file.id}_${file.name}")
                context.contentResolver.openInputStream(Uri.parse(file.path))?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0) tempFile else null
            } else {
                val f = File(file.path)
                if (f.exists()) {
                    f
                } else if (file.id.startsWith("media_")) {
                    val mediaId = file.id.removePrefix("media_").toLongOrNull()
                    if (mediaId != null) {
                        val queryUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                        } else {
                            MediaStore.Files.getContentUri("external")
                        }
                        val contentUri = android.content.ContentUris.withAppendedId(queryUri, mediaId)
                        val cacheDir = File(context.cacheDir, "shared_phone_files").apply { mkdirs() }
                        val tempFile = File(cacheDir, "${file.id}_${file.name}")
                        context.contentResolver.openInputStream(contentUri)?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 0) tempFile else null
                    } else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            "mp4", "mkv" -> "video/mp4"
            "mp3", "m4a", "wav" -> "audio/mpeg"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
