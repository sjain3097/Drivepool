package com.example.drivepool.data.model

import java.util.Locale

data class DriveNode(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val role: NodeRole = NodeRole.WORKER,
    val totalBytes: Long = 15L * 1024 * 1024 * 1024, // 15 GB default Google Drive free quota
    val usedBytes: Long = 0L,
    val isOnline: Boolean = true,
    val isBufferProtected: Boolean = false, // true when nearing capacity to protect Master index
    val externalUsageBytes: Long = 0L // Usage outside of DrivePool (e.g. personal Gmail/Photos)
) {
    val freeBytes: Long
        get() = maxOf(0L, totalBytes - usedBytes)

    val usagePercent: Float
        get() = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes * 100).toFloat() else 0f

    val formattedUsed: String
        get() = formatBytes(usedBytes)

    val formattedTotal: String
        get() = formatBytes(totalBytes)

    val formattedFree: String
        get() = formatBytes(freeBytes)

    companion object {
        const val KB = 1024L
        const val MB = 1024L * 1024
        const val GB = 1024L * 1024 * 1024
        const val DEFAULT_QUOTA_BYTES = 15L * GB

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val index = digitGroups.coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, index.toDouble())
            return String.format(Locale.US, "%.1f %s", value, units[index])
        }
    }
}
