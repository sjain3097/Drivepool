package com.example.drivepool.data.remote

import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.PoolFile
import java.io.InputStream

interface GoogleDriveService {
    suspend fun fetchAccountQuota(accountEmail: String): Pair<Long, Long> // totalBytes, usedBytes
    suspend fun uploadFile(
        targetNode: DriveNode,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        inputStream: InputStream?
    ): PoolFile
    suspend fun deleteFile(node: DriveNode, remoteFileId: String): Boolean
    suspend fun uploadMasterIndex(masterNode: DriveNode, indexJson: String): Boolean
    suspend fun downloadMasterIndex(masterNode: DriveNode): String?
}
