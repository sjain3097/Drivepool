package com.example.drivepool.data.master

import android.content.Context
import com.example.drivepool.data.model.CatalogFileEntry
import com.example.drivepool.data.model.CatalogNodeEntry
import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.FileCategory
import com.example.drivepool.data.model.MasterCatalog
import com.example.drivepool.data.model.PoolFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class MasterIndexManager(
    private val context: Context
) {
    private val mutex = Mutex()
    private val indexFileName = "master_index.json"
    private var currentCatalog: MasterCatalog? = null

    /**
     * Initializes or loads the Master Catalog from the local cache file.
     */
    suspend fun loadCatalog(fallbackMasterEmail: String): MasterCatalog = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, indexFileName)
            if (file.exists()) {
                try {
                    val content = file.readText()
                    val parsed = MasterCatalog.fromJson(content)
                    currentCatalog = parsed
                    return@withContext parsed
                } catch (_: Exception) {
                    // Fall through to initialize fresh
                }
            }

            // Create initial catalog
            val initial = MasterCatalog(
                version = 1,
                clusterId = "cluster_drivepool_primary",
                masterNodeId = fallbackMasterEmail,
                lastUpdated = System.currentTimeMillis(),
                nodes = emptyList(),
                files = emptyList()
            )
            saveCatalogInternal(initial)
            currentCatalog = initial
            initial
        }
    }

    /**
     * Persists the catalog to local disk and returns byte size.
     */
    suspend fun saveCatalog(catalog: MasterCatalog): Long = mutex.withLock {
        withContext(Dispatchers.IO) {
            saveCatalogInternal(catalog)
        }
    }

    private fun saveCatalogInternal(catalog: MasterCatalog): Long {
        currentCatalog = catalog
        val json = catalog.toJson()
        val file = File(context.filesDir, indexFileName)
        file.writeText(json)
        return file.length()
    }

    suspend fun clearCatalog() = mutex.withLock {
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, indexFileName)
            if (file.exists()) {
                file.delete()
            }
            currentCatalog = null
        }
    }

    /**
     * Fast search across catalog files in memory (sub-millisecond search).
     */
    fun searchFiles(
        files: List<PoolFile>,
        query: String,
        category: FileCategory = FileCategory.ALL
    ): List<PoolFile> {
        val trimmed = query.trim().lowercase()

        return files.filter { file ->
            // Category filter
            val matchesCategory = (category == FileCategory.ALL || file.category == category)
            if (!matchesCategory) return@filter false

            // Search query filter
            if (trimmed.isEmpty()) return@filter true

            file.name.lowercase().contains(trimmed) ||
                    file.virtualPath.lowercase().contains(trimmed) ||
                    file.mimeType.lowercase().contains(trimmed) ||
                    file.physicalNodeEmail.lowercase().contains(trimmed)
        }
    }

    /**
     * Generates a syncable MasterCatalog from the current nodes and files.
     */
    fun buildCatalog(
        masterNode: DriveNode,
        nodes: List<DriveNode>,
        files: List<PoolFile>
    ): MasterCatalog {
        val nodeEntries = nodes.map {
            CatalogNodeEntry(
                id = it.id,
                email = it.email,
                displayName = it.displayName,
                role = it.role,
                totalBytes = it.totalBytes,
                usedBytes = it.usedBytes
            )
        }

        val fileEntries = files.map {
            CatalogFileEntry(
                virtualId = it.id,
                name = it.name,
                virtualPath = it.virtualPath,
                sizeBytes = it.sizeBytes,
                mimeType = it.mimeType,
                modifiedTime = it.modifiedTime,
                physicalNodeId = it.physicalNodeId,
                physicalNodeEmail = it.physicalNodeEmail,
                remoteDriveFileId = it.remoteDriveFileId,
                remoteDrivePath = it.remoteDrivePath,
                checksumSha256 = it.checksumSha256,
                isStarred = it.isStarred
            )
        }

        return MasterCatalog(
            version = 1,
            clusterId = "cluster_drivepool_primary",
            masterNodeId = masterNode.id,
            lastUpdated = System.currentTimeMillis(),
            nodes = nodeEntries,
            files = fileEntries
        )
    }

    /**
     * Returns size of cached master index file.
     */
    fun getIndexFileSize(): Long {
        val file = File(context.filesDir, indexFileName)
        return if (file.exists()) file.length() else 0L
    }
}
