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

data class PhoneCategorySummary(
    val category: FileCategory,
    val fileCount: Int,
    val totalSizeBytes: Long,
    val percentageOfTotal: Float = 0f,
    val files: List<LocalPhoneFile> = emptyList()
) {
    val formattedTotalSize: String
        get() = DriveNode.formatBytes(totalSizeBytes)
}

data class PhoneDuplicateGroup(
    val fileName: String,
    val sizeBytes: Long,
    val files: List<LocalPhoneFile>
) {
    val formattedSize: String
        get() = DriveNode.formatBytes(sizeBytes)

    val potentialSavingsBytes: Long
        get() = if (files.size > 1) (files.size - 1) * sizeBytes else 0L

    val formattedSavings: String
        get() = DriveNode.formatBytes(potentialSavingsBytes)
}

data class CloudOffloadCandidate(
    val phoneFile: LocalPhoneFile,
    val matchedCloudFile: PoolFile
) {
    val formattedSize: String
        get() = DriveNode.formatBytes(phoneFile.sizeBytes)
}

data class PhoneOrganizerInsights(
    val categorySummaries: List<PhoneCategorySummary> = emptyList(),
    val largestFiles: List<LocalPhoneFile> = emptyList(),
    val duplicateCandidates: List<PhoneDuplicateGroup> = emptyList(),
    val offloadCandidates: List<CloudOffloadCandidate> = emptyList(),
    val totalScannedFiles: Int = 0,
    val totalScannedBytes: Long = 0L,
    val totalOffloadSavingsBytes: Long = 0L,
    val deviceStorageInfo: com.example.drivepool.data.local.DeviceStorageInfo = com.example.drivepool.data.local.DeviceStorageInfo(0L, 0L, 0L, 512)
) {
    val formattedTotalScanned: String
        get() = DriveNode.formatBytes(totalScannedBytes)

    val formattedOffloadSavings: String
        get() = DriveNode.formatBytes(totalOffloadSavingsBytes)
}

