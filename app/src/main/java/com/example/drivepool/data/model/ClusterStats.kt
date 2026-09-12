package com.example.drivepool.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MasterHealthStatus {
    HEALTHY,            // Master has ample headroom (> 500 MB free)
    BUFFER_ACTIVE,      // Master free space < 500 MB, payload uploads blocked, index protected
    CRITICAL_FULL       // Master drive full, dynamic failover recommended
}

data class ClusterStats(
    val totalStorageBytes: Long = 0L,
    val usedStorageBytes: Long = 0L,
    val masterNodeEmail: String = "",
    val masterHealth: MasterHealthStatus = MasterHealthStatus.HEALTHY,
    val masterFreeBytes: Long = 0L,
    val nodeCount: Int = 0,
    val fileCount: Int = 0,
    val balanceScorePercent: Int = 100, // 0 - 100% how equally storage is divided
    val catalogEntriesCount: Int = 0,
    val catalogSizeBytes: Long = 0L,
    val lastIndexSyncTime: Long = System.currentTimeMillis()
) {
    val freeStorageBytes: Long
        get() = maxOf(0L, totalStorageBytes - usedStorageBytes)

    val overallUsagePercent: Float
        get() = if (totalStorageBytes > 0) (usedStorageBytes.toDouble() / totalStorageBytes * 100).toFloat() else 0f

    val formattedTotal: String
        get() = DriveNode.formatBytes(totalStorageBytes)

    val formattedUsed: String
        get() = DriveNode.formatBytes(usedStorageBytes)

    val formattedFree: String
        get() = DriveNode.formatBytes(freeStorageBytes)

    val formattedCatalogSize: String
        get() = DriveNode.formatBytes(catalogSizeBytes)

    val formattedSyncTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastIndexSyncTime))
}
