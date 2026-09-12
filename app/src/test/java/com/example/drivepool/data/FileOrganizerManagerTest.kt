package com.example.drivepool.data

import com.example.drivepool.data.master.FileOrganizerManager
import com.example.drivepool.data.model.FileCategory
import com.example.drivepool.data.model.PoolFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOrganizerManagerTest {

    private val organizer = FileOrganizerManager()
    private val mb = 1024L * 1024

    @Test
    fun testCategoryIndexingAndSummaries() {
        val files = listOf(
            PoolFile(id = "1", name = "Report.pdf", sizeBytes = 10 * mb, mimeType = "application/pdf", physicalNodeId = "n1", physicalNodeEmail = "n1@gmail.com", remoteDriveFileId = "r1"),
            PoolFile(id = "2", name = "Invoice.docx", sizeBytes = 5 * mb, mimeType = "application/msword", physicalNodeId = "n2", physicalNodeEmail = "n2@gmail.com", remoteDriveFileId = "r2"),
            PoolFile(id = "3", name = "Video.mp4", sizeBytes = 500 * mb, mimeType = "video/mp4", physicalNodeId = "n1", physicalNodeEmail = "n1@gmail.com", remoteDriveFileId = "r3"),
            PoolFile(id = "4", name = "Photo.jpg", sizeBytes = 3 * mb, mimeType = "image/jpeg", physicalNodeId = "n2", physicalNodeEmail = "n2@gmail.com", remoteDriveFileId = "r4")
        )

        val insights = organizer.analyzeIndex(files)

        assertEquals(4, insights.totalIndexedFiles)
        assertEquals(518L * mb, insights.totalIndexedBytes)

        val docSummary = insights.categorySummaries.find { it.category == FileCategory.DOCUMENTS }
        assertEquals(2, docSummary?.fileCount)
        assertEquals(15L * mb, docSummary?.totalSizeBytes)

        val videoSummary = insights.categorySummaries.find { it.category == FileCategory.VIDEOS }
        assertEquals(1, videoSummary?.fileCount)
        assertEquals(500L * mb, videoSummary?.totalSizeBytes)
    }

    @Test
    fun testDuplicateDetection() {
        val files = listOf(
            PoolFile(id = "1", name = "Backup.zip", sizeBytes = 100 * mb, checksumSha256 = "hash123", physicalNodeId = "n1", physicalNodeEmail = "n1@gmail.com", remoteDriveFileId = "r1"),
            PoolFile(id = "2", name = "Backup.zip", sizeBytes = 100 * mb, checksumSha256 = "hash123", physicalNodeId = "n2", physicalNodeEmail = "n2@gmail.com", remoteDriveFileId = "r2")
        )

        val insights = organizer.analyzeIndex(files)

        assertEquals(1, insights.duplicateCandidates.size)
        assertEquals("Backup.zip", insights.duplicateCandidates[0].fileName)
        assertEquals(2, insights.duplicateCandidates[0].files.size)
    }

    @Test
    fun testAutoOrganizeVirtualPaths() {
        val files = listOf(
            PoolFile(id = "1", name = "Doc.pdf", sizeBytes = 10 * mb, virtualPath = "/Unorganized/", physicalNodeId = "n1", physicalNodeEmail = "n1@gmail.com", remoteDriveFileId = "r1"),
            PoolFile(id = "2", name = "Clip.mp4", sizeBytes = 50 * mb, virtualPath = "/Downloads/", physicalNodeId = "n2", physicalNodeEmail = "n2@gmail.com", remoteDriveFileId = "r2")
        )

        val organized = organizer.autoOrganizeFiles(files)

        assertEquals("/CloudPool/Documents/", organized[0].virtualPath)
        assertEquals("/CloudPool/Videos/", organized[1].virtualPath)
    }
}
