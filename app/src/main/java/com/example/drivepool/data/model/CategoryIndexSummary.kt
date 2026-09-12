package com.example.drivepool.data.model

data class CategoryIndexSummary(
    val category: FileCategory,
    val fileCount: Int,
    val totalSizeBytes: Long,
    val percentageOfTotal: Float = 0f,
    val files: List<PoolFile> = emptyList()
) {
    val formattedTotalSize: String
        get() = DriveNode.formatBytes(totalSizeBytes)
}

data class DuplicateGroup(
    val fileName: String,
    val sizeBytes: Long,
    val files: List<PoolFile>
) {
    val formattedSize: String
        get() = DriveNode.formatBytes(sizeBytes)

    val potentialSavingsBytes: Long
        get() = if (files.size > 1) (files.size - 1) * sizeBytes else 0L

    val formattedSavings: String
        get() = DriveNode.formatBytes(potentialSavingsBytes)
}

data class OrganizerInsights(
    val categorySummaries: List<CategoryIndexSummary> = emptyList(),
    val largestFiles: List<PoolFile> = emptyList(),
    val duplicateCandidates: List<DuplicateGroup> = emptyList(),
    val totalIndexedFiles: Int = 0,
    val totalIndexedBytes: Long = 0L,
    val isOrganized: Boolean = false
) {
    val formattedTotalIndexed: String
        get() = DriveNode.formatBytes(totalIndexedBytes)
}
