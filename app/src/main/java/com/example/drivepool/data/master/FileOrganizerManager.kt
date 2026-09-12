package com.example.drivepool.data.master

import com.example.drivepool.data.model.CategoryIndexSummary
import com.example.drivepool.data.model.DuplicateGroup
import com.example.drivepool.data.model.FileCategory
import com.example.drivepool.data.model.OrganizerInsights
import com.example.drivepool.data.model.PoolFile

class FileOrganizerManager {

    /**
     * Builds complete Organizer insights from the indexed files.
     */
    fun analyzeIndex(files: List<PoolFile>): OrganizerInsights {
        if (files.isEmpty()) return OrganizerInsights()

        val totalBytes = files.sumOf { it.sizeBytes }

        // Group by category (excluding ALL)
        val categoryOrder = listOf(
            FileCategory.DOCUMENTS,
            FileCategory.VIDEOS,
            FileCategory.IMAGES,
            FileCategory.AUDIO,
            FileCategory.ARCHIVES,
            FileCategory.OTHER
        )

        val summaries = categoryOrder.map { category ->
            val catFiles = files.filter { it.category == category }
            val catBytes = catFiles.sumOf { it.sizeBytes }
            val percent = if (totalBytes > 0) (catBytes.toDouble() / totalBytes * 100).toFloat() else 0f

            CategoryIndexSummary(
                category = category,
                fileCount = catFiles.size,
                totalSizeBytes = catBytes,
                percentageOfTotal = percent,
                files = catFiles.sortedByDescending { it.sizeBytes }
            )
        }.filter { it.fileCount > 0 }

        // Top largest files
        val largest = files.sortedByDescending { it.sizeBytes }.take(5)

        // Duplicate detection: group by checksum or (name + size)
        val duplicates = files.groupBy {
            if (it.checksumSha256.isNotBlank()) it.checksumSha256 else "${it.name}_${it.sizeBytes}"
        }.filter { it.value.size > 1 }
            .map { entry ->
                val sample = entry.value.first()
                DuplicateGroup(
                    fileName = sample.name,
                    sizeBytes = sample.sizeBytes,
                    files = entry.value
                )
            }

        val allOrganized = files.all { it.virtualPath.startsWith("/CloudPool/") }

        return OrganizerInsights(
            categorySummaries = summaries,
            largestFiles = largest,
            duplicateCandidates = duplicates,
            totalIndexedFiles = files.size,
            totalIndexedBytes = totalBytes,
            isOrganized = allOrganized
        )
    }

    /**
     * Auto-organizes files into structured virtual folders based on their indexed category.
     */
    fun autoOrganizeFiles(files: List<PoolFile>): List<PoolFile> {
        return files.map { file ->
            val targetFolder = when (file.category) {
                FileCategory.DOCUMENTS -> "/CloudPool/Documents/"
                FileCategory.VIDEOS -> "/CloudPool/Videos/"
                FileCategory.IMAGES -> "/CloudPool/Photos/"
                FileCategory.AUDIO -> "/CloudPool/Audio/"
                FileCategory.ARCHIVES -> "/CloudPool/Archives/"
                FileCategory.ALL, FileCategory.OTHER -> "/CloudPool/Miscellaneous/"
            }

            file.copy(virtualPath = targetFolder)
        }
    }
}
