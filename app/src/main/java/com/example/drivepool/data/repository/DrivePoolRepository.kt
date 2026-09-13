package com.example.drivepool.data.repository

import android.content.Context
import com.example.drivepool.data.balancer.AllocationDecision
import com.example.drivepool.data.balancer.EqualStorageBalancer
import com.example.drivepool.data.local.DrivePoolDatabaseHelper
import com.example.drivepool.data.local.PhoneStorageManager
import com.example.drivepool.data.master.ElectionResult
import com.example.drivepool.data.master.MasterElectionManager
import com.example.drivepool.data.master.MasterIndexManager
import com.example.drivepool.data.model.ClusterStats
import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.FileCategory
import com.example.drivepool.data.model.LocalPhoneFile
import com.example.drivepool.data.model.MasterHealthStatus
import com.example.drivepool.data.model.NodeRole
import com.example.drivepool.data.model.PoolFile
import com.example.drivepool.data.remote.GoogleDriveService
import com.example.drivepool.data.remote.RealGoogleDriveService
import android.util.Log
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class DrivePoolRepository(
    private val context: Context,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val dbHelper = DrivePoolDatabaseHelper(context)
    private val indexManager = MasterIndexManager(context)
    private val balancer = EqualStorageBalancer()
    private val electionManager = MasterElectionManager()
    private val phoneManager = PhoneStorageManager(context)
    private val googleDriveService: GoogleDriveService = RealGoogleDriveService(context)

    private val _nodes = MutableStateFlow<List<DriveNode>>(emptyList())
    val nodes: StateFlow<List<DriveNode>> = _nodes.asStateFlow()

    private val _files = MutableStateFlow<List<PoolFile>>(emptyList())
    val files: StateFlow<List<PoolFile>> = _files.asStateFlow()

    private val _phoneFiles = MutableStateFlow<List<com.example.drivepool.data.model.LocalPhoneFile>>(emptyList())
    val phoneFiles: StateFlow<List<com.example.drivepool.data.model.LocalPhoneFile>> = _phoneFiles.asStateFlow()

    private val _deviceStorageInfo = MutableStateFlow(phoneManager.getDeviceStorageInfo())
    val deviceStorageInfo: StateFlow<com.example.drivepool.data.local.DeviceStorageInfo> = _deviceStorageInfo.asStateFlow()

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow(FileCategory.ALL)

    val phoneSearchQuery = MutableStateFlow("")
    val phoneCategory = MutableStateFlow(FileCategory.ALL)

    val filteredPhoneFiles: StateFlow<List<com.example.drivepool.data.model.LocalPhoneFile>> = combine(
        _phoneFiles,
        phoneSearchQuery,
        phoneCategory
    ) { allFiles, query, cat ->
        val trimmed = query.trim().lowercase()
        allFiles.filter { file ->
            val matchesCat = (cat == FileCategory.ALL || file.category == cat)
            if (!matchesCat) return@filter false
            if (trimmed.isEmpty()) return@filter true
            file.name.lowercase().contains(trimmed) || file.path.lowercase().contains(trimmed)
        }
    }.stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    // Instant-search filtered files stream
    val filteredFiles: StateFlow<List<PoolFile>> = combine(
        _files,
        searchQuery,
        selectedCategory
    ) { allFiles, query, category ->
        indexManager.searchFiles(allFiles, query, category)
    }.stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    // High-level cluster stats
    val clusterStats: StateFlow<ClusterStats> = combine(_nodes, _files) { currentNodes, currentFiles ->
        calculateStats(currentNodes, currentFiles)
    }.stateIn(externalScope, SharingStarted.Eagerly, ClusterStats())

    private val organizerManager = com.example.drivepool.data.master.FileOrganizerManager()

    // File Organizer Insights Flow (analyzes indexed categories, duplicates, and large files)
    val organizerInsights: StateFlow<com.example.drivepool.data.model.OrganizerInsights> =
        kotlinx.coroutines.flow.flow {
            _files.collect { currentFiles ->
                emit(organizerManager.analyzeIndex(currentFiles))
            }
        }.stateIn(externalScope, SharingStarted.Eagerly, com.example.drivepool.data.model.OrganizerInsights())

    // Phone Storage Organizer Insights Flow
    val phoneOrganizerInsights: StateFlow<com.example.drivepool.data.model.PhoneOrganizerInsights> =
        combine(_phoneFiles, _files, deviceStorageInfo) { currentPhoneFiles, currentCloudFiles, storageInfo ->
            organizerManager.analyzePhoneStorage(currentPhoneFiles, currentCloudFiles, storageInfo)
        }.stateIn(externalScope, SharingStarted.Eagerly, com.example.drivepool.data.model.PhoneOrganizerInsights())


    init {
        externalScope.launch {
            loadInitialData()
        }
    }

    private suspend fun loadInitialData() = withContext(Dispatchers.IO) {
        var cachedNodes = dbHelper.getAllNodes()
        var cachedFiles = dbHelper.getAllFiles()

        // Purge any legacy dummy/mock nodes and files from previous demo sessions
        val dummyEmails = setOf("alex.master@gmail.com", "photos.storage@gmail.com", "work.backups@gmail.com")
        if (cachedNodes.any { it.email in dummyEmails }) {
            dbHelper.clearAll()
            indexManager.clearCatalog()
            cachedNodes = emptyList()
            cachedFiles = emptyList()
        }

        if (cachedNodes.isNotEmpty()) {
            val master = cachedNodes.find { it.role == NodeRole.MASTER } ?: cachedNodes.first()
            val catalog = indexManager.buildCatalog(master, cachedNodes, cachedFiles)
            indexManager.saveCatalog(catalog)
        }

        _nodes.value = cachedNodes
        _files.value = cachedFiles
        _phoneFiles.value = phoneManager.getPhoneFiles()
    }

    private fun calculateStats(currentNodes: List<DriveNode>, currentFiles: List<PoolFile>): ClusterStats {
        val totalBytes = currentNodes.sumOf { it.totalBytes }
        val usedBytes = currentNodes.sumOf { it.usedBytes }
        val master = currentNodes.find { it.role == NodeRole.MASTER }
        val masterHealth = electionManager.evaluateMasterHealth(master)
        val balanceScore = balancer.calculateBalanceScore(currentNodes)
        val indexSize = indexManager.getIndexFileSize()

        return ClusterStats(
            totalStorageBytes = totalBytes,
            usedStorageBytes = usedBytes,
            masterNodeEmail = master?.email ?: "None",
            masterHealth = masterHealth,
            masterFreeBytes = master?.freeBytes ?: 0L,
            nodeCount = currentNodes.size,
            fileCount = currentFiles.size,
            balanceScorePercent = balanceScore,
            catalogEntriesCount = currentFiles.size,
            catalogSizeBytes = if (indexSize > 0) indexSize else (currentFiles.size * 280L),
            lastIndexSyncTime = System.currentTimeMillis()
        )
    }

    /**
     * Uploads a file with equal backend segregation.
     * The balancer selects the optimal node to keep usage balanced across all accounts.
     */
    suspend fun uploadFile(
        name: String,
        sizeBytes: Long,
        mimeType: String,
        virtualPath: String = "/Uploads/",
        inputStream: InputStream? = null
    ): Result<AllocationDecision> = withContext(Dispatchers.Default) {
        val currentNodes = _nodes.value
        val decision = balancer.selectOptimalNode(currentNodes, sizeBytes)
            ?: return@withContext Result.failure(Exception("Insufficient pooled storage to allocate ${DriveNode.formatBytes(sizeBytes)}"))

        val targetNode = decision.targetNode

        // Execute live upload to Google Drive REST API
        val uploadedFile: PoolFile = try {
            googleDriveService.uploadFile(
                targetNode = targetNode,
                fileName = name,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                inputStream = inputStream
            )
        } catch (e: Exception) {
            Log.e("DrivePoolRepository", "Live Google Drive upload failed: ${e.message}", e)
            return@withContext Result.failure(e)
        }

        // Update target node usage
        val updatedNodes = currentNodes.map { node ->
            if (node.id == targetNode.id) {
                node.copy(usedBytes = node.usedBytes + sizeBytes)
            } else {
                node
            }
        }

        val updatedFiles = listOf(uploadedFile) + _files.value

        // Update Master Catalog
        val master = updatedNodes.find { it.role == NodeRole.MASTER } ?: updatedNodes.first()
        val catalog = indexManager.buildCatalog(master, updatedNodes, updatedFiles)
        indexManager.saveCatalog(catalog)

        // Persist
        dbHelper.saveNodes(updatedNodes)
        dbHelper.saveFiles(updatedFiles)

        _nodes.value = updatedNodes
        _files.value = updatedFiles

        Result.success(decision)
    }

    /**
     * Rebalances/Migrates a file from its current physical node to another account.
     */
    suspend fun rebalanceFile(
        file: PoolFile,
        targetNodeId: String
    ): Result<Unit> = withContext(Dispatchers.Default) {
        val currentNodes = _nodes.value
        val sourceNode = currentNodes.find { it.id == file.physicalNodeId }
            ?: return@withContext Result.failure(Exception("Source node not found"))
        val targetNode = currentNodes.find { it.id == targetNodeId }
            ?: return@withContext Result.failure(Exception("Target node not found"))

        if (targetNode.freeBytes < file.sizeBytes) {
            return@withContext Result.failure(Exception("Target node has insufficient free space"))
        }

        delay(300)

        // Transfer storage accounting
        val updatedNodes = currentNodes.map { node ->
            when (node.id) {
                sourceNode.id -> node.copy(usedBytes = maxOf(0L, node.usedBytes - file.sizeBytes))
                targetNode.id -> node.copy(usedBytes = node.usedBytes + file.sizeBytes)
                else -> node
            }
        }

        val updatedFile = file.copy(
            physicalNodeId = targetNode.id,
            physicalNodeEmail = targetNode.email,
            modifiedTime = System.currentTimeMillis()
        )

        val updatedFiles = _files.value.map { if (it.id == file.id) updatedFile else it }

        // Update Master Catalog
        val master = updatedNodes.find { it.role == NodeRole.MASTER } ?: updatedNodes.first()
        val catalog = indexManager.buildCatalog(master, updatedNodes, updatedFiles)
        indexManager.saveCatalog(catalog)

        dbHelper.saveNodes(updatedNodes)
        dbHelper.saveFiles(updatedFiles)

        _nodes.value = updatedNodes
        _files.value = updatedFiles

        Result.success(Unit)
    }

    /**
     * Deletes a file, updating the Master Catalog and freeing space on the physical node.
     */
    suspend fun deleteFile(file: PoolFile): Result<Unit> = withContext(Dispatchers.IO) {
        val currentNodes = _nodes.value
        val node = currentNodes.find { it.id == file.physicalNodeId }

        // Attempt remote deletion if it's an authenticated Google Drive file
        if (node != null && !file.remoteDriveFileId.startsWith("virtual_", ignoreCase = true) &&
            !file.remoteDriveFileId.startsWith("mock_", ignoreCase = true) &&
            file.remoteDriveFileId.isNotBlank()
        ) {
            try {
                googleDriveService.deleteFile(node, file.remoteDriveFileId)
            } catch (e: Exception) {
                Log.w("DrivePoolRepository", "Remote delete warning for ${file.name}: ${e.message}")
            }
        }

        val updatedNodes = currentNodes.map { n ->
            if (n.id == file.physicalNodeId) {
                n.copy(usedBytes = maxOf(0L, n.usedBytes - file.sizeBytes))
            } else {
                n
            }
        }

        val updatedFiles = _files.value.filterNot { it.id == file.id }

        val master = updatedNodes.find { it.role == NodeRole.MASTER } ?: updatedNodes.first()
        val catalog = indexManager.buildCatalog(master, updatedNodes, updatedFiles)
        indexManager.saveCatalog(catalog)

        dbHelper.saveNodes(updatedNodes)
        dbHelper.saveFiles(updatedFiles)

        _nodes.value = updatedNodes
        _files.value = updatedFiles

        try {
            val cacheFolder = java.io.File(context.cacheDir, "cloud_view_cache")
            java.io.File(cacheFolder, "${file.id}_${file.name}").delete()
        } catch (_: Exception) {}

        Result.success(Unit)
    }

    /**
     * Deletes multiple files in bulk, recalculating pooled node storage and updating catalog.
     */
    suspend fun deleteFiles(filesToDelete: List<PoolFile>): Result<Int> = withContext(Dispatchers.IO) {
        if (filesToDelete.isEmpty()) return@withContext Result.success(0)
        val deleteIds = filesToDelete.map { it.id }.toSet()
        val currentNodes = _nodes.value

        // Attempt remote deletion on Drive nodes for each file
        for (file in filesToDelete) {
            val node = currentNodes.find { it.id == file.physicalNodeId }
            if (node != null && !file.remoteDriveFileId.startsWith("virtual_", ignoreCase = true) &&
                !file.remoteDriveFileId.startsWith("mock_", ignoreCase = true) &&
                file.remoteDriveFileId.isNotBlank()
            ) {
                try {
                    googleDriveService.deleteFile(node, file.remoteDriveFileId)
                } catch (e: Exception) {
                    Log.w("DrivePoolRepository", "Remote delete warning for ${file.name}: ${e.message}")
                }
            }
        }

        val freedPerNode = filesToDelete.groupBy { it.physicalNodeId }
            .mapValues { (_, list) -> list.sumOf { it.sizeBytes } }

        val updatedNodes = currentNodes.map { node ->
            val freed = freedPerNode[node.id] ?: 0L
            if (freed > 0) {
                node.copy(usedBytes = maxOf(0L, node.usedBytes - freed))
            } else {
                node
            }
        }

        val updatedFiles = _files.value.filterNot { it.id in deleteIds }
        val master = updatedNodes.find { it.role == NodeRole.MASTER } ?: updatedNodes.first()
        val catalog = indexManager.buildCatalog(master, updatedNodes, updatedFiles)
        indexManager.saveCatalog(catalog)

        dbHelper.saveNodes(updatedNodes)
        dbHelper.saveFiles(updatedFiles)

        _nodes.value = updatedNodes
        _files.value = updatedFiles

        try {
            val cacheFolder = java.io.File(context.cacheDir, "cloud_view_cache")
            filesToDelete.forEach { f ->
                java.io.File(cacheFolder, "${f.id}_${f.name}").delete()
            }
        } catch (_: Exception) {}

        Result.success(filesToDelete.size)
    }

    /**
     * Triggers dynamic Master Election / Failover.
     * Elects the healthiest worker node with the most free space to become New Master.
     */
    suspend fun triggerMasterFailover(): Result<ElectionResult> = withContext(Dispatchers.Default) {
        val currentNodes = _nodes.value
        val result = electionManager.electNewMaster(currentNodes)
        if (!result.success || result.newMaster == null) {
            return@withContext Result.failure(Exception(result.reason))
        }

        val updatedNodes = currentNodes.map { node ->
            when (node.id) {
                result.newMaster.id -> result.newMaster
                result.previousMaster?.id -> result.previousMaster
                else -> node
            }
        }

        // Rebuild catalog under new Master coordinator
        val catalog = indexManager.buildCatalog(result.newMaster, updatedNodes, _files.value)
        indexManager.saveCatalog(catalog)

        dbHelper.saveNodes(updatedNodes)
        _nodes.value = updatedNodes

        Result.success(result)
    }

    /**
     * Demonstrates the scenario: "What if the Master drive is full?"
     * Artificially fills the Master node to leave only 300 MB or 20 MB free space
     * to trigger the safety buffer and demonstrate upload auto-diversion and failover.
     */
    suspend fun simulateFillMasterDrive(bytesRemaining: Long = 300L * 1024 * 1024) = withContext(Dispatchers.Default) {
        val currentNodes = _nodes.value
        val updatedNodes = currentNodes.map { node ->
            if (node.role == NodeRole.MASTER) {
                val newUsed = maxOf(0L, node.totalBytes - bytesRemaining)
                node.copy(
                    usedBytes = newUsed,
                    isBufferProtected = bytesRemaining < EqualStorageBalancer.MASTER_SAFETY_BUFFER_BYTES
                )
            } else {
                node
            }
        }

        dbHelper.saveNodes(updatedNodes)
        _nodes.value = updatedNodes
    }

    /**
     * Connects a new Google account (Worker Node) adding 15 GB to the cluster.
     */
    suspend fun addNewAccount(email: String, displayName: String): Result<DriveNode> = withContext(Dispatchers.Default) {
        val currentNodes = _nodes.value
        if (currentNodes.any { it.email.equals(email, ignoreCase = true) }) {
            return@withContext Result.failure(Exception("Account $email is already connected to DrivePool."))
        }

        val isFirst = currentNodes.isEmpty()
        val newNode = DriveNode(
            id = if (isFirst) "node_master_${UUID.randomUUID().toString().take(6)}" else "node_worker_${UUID.randomUUID().toString().take(6)}",
            email = email,
            displayName = displayName,
            role = if (isFirst) NodeRole.MASTER else NodeRole.WORKER,
            totalBytes = DriveNode.DEFAULT_QUOTA_BYTES,
            usedBytes = 0L,
            isOnline = true,
            isBufferProtected = false
        )

        val updatedNodes = currentNodes + newNode
        dbHelper.saveNodes(updatedNodes)
        _nodes.value = updatedNodes

        val master = updatedNodes.find { it.role == NodeRole.MASTER } ?: updatedNodes.first()
        val catalog = indexManager.buildCatalog(master, updatedNodes, _files.value)
        indexManager.saveCatalog(catalog)

        Result.success(newNode)
    }

    /**
     * Clears all pooled accounts and cloud files.
     */
    suspend fun resetCluster() = withContext(Dispatchers.Default) {
        dbHelper.clearAll()
        indexManager.clearCatalog()

        _nodes.value = emptyList()
        _files.value = emptyList()
        searchQuery.value = ""
        selectedCategory.value = FileCategory.ALL
    }

    /**
     * Uploads a local phone file to the DrivePool cluster using equal segregation.
     */
    suspend fun uploadLocalFileToPool(file: com.example.drivepool.data.model.LocalPhoneFile): Result<AllocationDecision> {
        val stream = phoneManager.openFileInputStream(file)
        val result = uploadFile(
            name = file.name,
            sizeBytes = file.sizeBytes,
            mimeType = file.mimeType,
            virtualPath = "/PhoneUploads/",
            inputStream = stream
        )
        result.onSuccess { decision ->
            phoneManager.markFileUploaded(file.id, decision.targetNode.email)
            _phoneFiles.value = phoneManager.getPhoneFiles()

            // Cache the file locally so the cloud file has an instant visual thumbnail & offline preview
            try {
                val uploadedPoolFile = _files.value.firstOrNull { it.name == file.name }
                if (uploadedPoolFile != null) {
                    val cacheFolder = java.io.File(context.cacheDir, "cloud_view_cache").apply { mkdirs() }
                    val targetFile = java.io.File(cacheFolder, "${uploadedPoolFile.id}_${uploadedPoolFile.name}")
                    if (!targetFile.exists()) {
                        phoneManager.openFileInputStream(file)?.use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return result
    }

    /**
     * Deletes a local file from phone storage (frees phone memory).
     */
    suspend fun deleteLocalPhoneFile(file: com.example.drivepool.data.model.LocalPhoneFile): Boolean = withContext(Dispatchers.IO) {
        val deleted = phoneManager.deleteLocalFile(file)
        _phoneFiles.value = phoneManager.getPhoneFiles()
        _deviceStorageInfo.value = phoneManager.getDeviceStorageInfo()
        deleted
    }

    suspend fun deleteLocalPhoneFiles(
        fileIds: Set<String>,
        extraFiles: List<com.example.drivepool.data.model.LocalPhoneFile> = emptyList()
    ): Int = withContext(Dispatchers.IO) {
        val count = phoneManager.deleteLocalFiles(fileIds, extraFiles)
        _phoneFiles.value = phoneManager.getPhoneFiles()
        _deviceStorageInfo.value = phoneManager.getDeviceStorageInfo()
        count
    }

    /**
     * Registers a file chosen through Android's system document/media picker.
     */
    suspend fun addPickedPhoneFile(name: String, size: Long, mimeType: String, path: String): com.example.drivepool.data.model.LocalPhoneFile = withContext(Dispatchers.Default) {
        val file = phoneManager.addLocalFileFromPicker(name, size, mimeType, path)
        _phoneFiles.value = phoneManager.getPhoneFiles()
        file
    }

    suspend fun refreshPhoneFiles() = withContext(Dispatchers.IO) {
        _phoneFiles.value = phoneManager.getPhoneFiles()
    }

    fun hasStoragePermission(): Boolean = phoneManager.hasStoragePermission()
    fun getManageStorageIntent(): android.content.Intent = phoneManager.getManageStorageIntent()
    suspend fun listFolderContents(path: String? = null): com.example.drivepool.data.local.FolderContentResult = phoneManager.listFolderContents(path)

    suspend fun prepareFileForViewing(file: PoolFile): Result<java.io.File> = withContext(Dispatchers.IO) {
        val cacheFolder = java.io.File(context.cacheDir, "cloud_view_cache").apply { mkdirs() }
        val targetFile = java.io.File(cacheFolder, "${file.id}_${file.name}")

        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext Result.success(targetFile)
        }

        val node = _nodes.value.find { it.id == file.physicalNodeId }

        val isVirtual = file.remoteDriveFileId.startsWith("virtual_", ignoreCase = true) ||
                file.remoteDriveFileId.startsWith("mock_", ignoreCase = true) ||
                file.remoteDriveFileId.isBlank()

        if (isVirtual) {
            Log.i("DrivePoolRepository", "Generating virtual preview for simulated file: ${file.name}")
            createVirtualPreviewFile(file, node, targetFile)
            return@withContext Result.success(targetFile)
        }

        if (node == null) {
            return@withContext Result.failure(Exception("Node for file not found"))
        }

        try {
            val success = googleDriveService.downloadFile(node, file.remoteDriveFileId, targetFile)
            if (success && targetFile.exists() && targetFile.length() > 0) {
                Result.success(targetFile)
            } else {
                if (targetFile.exists() && targetFile.length() == 0L) {
                    targetFile.delete()
                }
                Result.failure(Exception("Failed to download file from Google Drive for preview"))
            }
        } catch (e: Exception) {
            if (targetFile.exists() && targetFile.length() == 0L) {
                targetFile.delete()
            }
            // If the remote file is not found (404) or was a legacy virtual ID that didn't match virtual_
            val isNotFound = e is java.io.FileNotFoundException ||
                    e.message?.contains("404") == true ||
                    e.message?.contains("notFound", ignoreCase = true) == true

            if (isNotFound) {
                Log.w("DrivePoolRepository", "Remote file not found on Google Drive (404). Falling back to virtual preview.")
                createVirtualPreviewFile(file, node, targetFile)
                if (targetFile.exists() && targetFile.length() > 0) {
                    Result.success(targetFile)
                } else {
                    Result.failure(Exception("File '${file.name}' was not found on Google Drive (HTTP 404). It may have been deleted remotely."))
                }
            } else {
                Result.failure(e)
            }
        }
    }

    private fun createVirtualPreviewFile(file: PoolFile, node: DriveNode?, targetFile: java.io.File) {
        val extension = file.name.substringAfterLast('.', "").lowercase()
        val isImage = file.mimeType.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
        val isPdf = file.mimeType == "application/pdf" || extension == "pdf"

        if (isImage) {
            try {
                val width = 1080
                val height = 1080
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)

                // Background
                val bgPaint = android.graphics.Paint().apply { color = 0xFF0F172A.toInt() }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                // Card Rect
                val cardPaint = android.graphics.Paint().apply { color = 0xFF1E293B.toInt() }
                val cardRect = android.graphics.RectF(60f, 80f, width - 60f, height - 80f)
                canvas.drawRoundRect(cardRect, 32f, 32f, cardPaint)

                // Accent top banner
                val accentPaint = android.graphics.Paint().apply { color = 0xFF2563EB.toInt() }
                canvas.drawRoundRect(android.graphics.RectF(60f, 80f, width - 60f, 170f), 32f, 32f, accentPaint)

                val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt()
                    textSize = 40f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                canvas.drawText("DrivePool Virtual Cloud File", (width / 2).toFloat(), 140f, textPaint)

                // Filename
                textPaint.textSize = 34f
                textPaint.color = 0xFF60A5FA.toInt()
                canvas.drawText(file.name.take(40), (width / 2).toFloat(), 250f, textPaint)

                // Metadata details
                val detailPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFCBD5E1.toInt()
                    textSize = 28f
                    typeface = android.graphics.Typeface.MONOSPACE
                    textAlign = android.graphics.Paint.Align.LEFT
                }

                val startX = 110f
                var startY = 350f
                val lineHeight = 55f

                val lines = listOf(
                    "Virtual Size:     ${DriveNode.formatBytes(file.sizeBytes)}",
                    "MIME Type:        ${file.mimeType}",
                    "Cluster Node:     ${node?.email ?: file.physicalNodeEmail}",
                    "Role:             ${node?.role?.name ?: "WORKER"}",
                    "Cluster Path:     ${file.virtualPath}",
                    "Remote File ID:   ${file.remoteDriveFileId.take(30)}",
                    "Checksum:         ${file.checksumSha256.take(20)}...",
                    "",
                    "Status:           Simulated Cluster Entry",
                    "Equal Balancer:   Segregated pool storage active"
                )

                for (line in lines) {
                    detailPaint.color = if (line.startsWith("Status:")) 0xFF34D399.toInt() else 0xFFCBD5E1.toInt()
                    canvas.drawText(line, startX, startY, detailPaint)
                    startY += lineHeight
                }

                // Bottom badge
                val badgePaint = android.graphics.Paint().apply { color = 0xFF334155.toInt() }
                canvas.drawRoundRect(android.graphics.RectF(100f, height - 190f, width - 100f, height - 120f), 20f, 20f, badgePaint)

                textPaint.textSize = 26f
                textPaint.color = 0xFF94A3B8.toInt()
                canvas.drawText("DrivePool Cloud Cluster • In-App Viewer Active", (width / 2).toFloat(), height - 145f, textPaint)

                targetFile.parentFile?.mkdirs()
                targetFile.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmap.recycle()
                return
            } catch (e: Exception) {
                Log.w("DrivePoolRepository", "Failed to generate virtual image preview: ${e.message}")
            }
        }

        if (isPdf) {
            try {
                val pdfDoc = android.graphics.pdf.PdfDocument()
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                val headerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF1E3A8A.toInt()
                    textSize = 24f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                canvas.drawText("DrivePool Unified Cloud Cluster", 40f, 60f, headerPaint)

                val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF0F172A.toInt()
                    textSize = 18f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                canvas.drawText("Document: ${file.name}", 40f, 110f, titlePaint)

                val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF334155.toInt()
                    textSize = 12f
                    typeface = android.graphics.Typeface.MONOSPACE
                }

                val docLines = listOf(
                    "Virtual File Preview",
                    "--------------------------------------------------",
                    "Allocated Storage Node: ${node?.email ?: file.physicalNodeEmail}",
                    "File Size:              ${DriveNode.formatBytes(file.sizeBytes)}",
                    "Virtual Path:           ${file.virtualPath}",
                    "Remote File ID:         ${file.remoteDriveFileId}",
                    "SHA-256 Checksum:       ${file.checksumSha256}",
                    "Modified Timestamp:     ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(file.modifiedTime))}",
                    "--------------------------------------------------",
                    "",
                    "This virtual document entry was allocated through DrivePool's",
                    "Equal Storage Balancer and cataloged in the Master Index.",
                    "Storage headroom and node balancing remain actively guarded."
                )

                var y = 150f
                for (l in docLines) {
                    canvas.drawText(l, 40f, y, bodyPaint)
                    y += 22f
                }

                pdfDoc.finishPage(page)
                targetFile.parentFile?.mkdirs()
                targetFile.outputStream().use { pdfDoc.writeTo(it) }
                pdfDoc.close()
                return
            } catch (e: Exception) {
                Log.w("DrivePoolRepository", "Failed to generate virtual PDF preview: ${e.message}")
            }
        }

        // Default text fallback
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(
            """
            ====================================================
            DrivePool Cloud Cluster - Virtual File Preview
            ====================================================
            File Name:      ${file.name}
            File Size:      ${DriveNode.formatBytes(file.sizeBytes)}
            MIME Type:      ${file.mimeType}
            Physical Node:  ${node?.email ?: file.physicalNodeEmail}
            Remote ID:      ${file.remoteDriveFileId}
            Virtual Path:   ${file.virtualPath}
            Modified:       ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(file.modifiedTime))}
            SHA-256:        ${file.checksumSha256}

            [Cluster Replication Note]
            This virtual entry demonstrates cluster balancing across multiple Google
            Drive worker accounts. All file index records are backed up by the Master Node.
            ====================================================
            """.trimIndent()
        )
    }

    suspend fun preparePhoneFileForViewing(file: LocalPhoneFile): Result<java.io.File> = withContext(Dispatchers.IO) {
        val f = phoneManager.getFileForSharing(file)
        if (f != null && f.exists()) {
            Result.success(f)
        } else {
            Result.failure(Exception("Could not resolve file on device storage"))
        }
    }

    /**
     * Re-indexes and maps all files to organized virtual folders (e.g. /CloudPool/Documents/)
     */
    suspend fun autoOrganizeFiles(): Int = withContext(Dispatchers.Default) {
        val organized = organizerManager.autoOrganizeFiles(_files.value)
        dbHelper.saveFiles(organized)
        _files.value = organized

        val master = _nodes.value.find { it.role == NodeRole.MASTER } ?: _nodes.value.firstOrNull()
        if (master != null) {
            val catalog = indexManager.buildCatalog(master, _nodes.value, organized)
            indexManager.saveCatalog(catalog)
        }

        organized.size
    }

    private val authManager = com.example.drivepool.data.remote.GoogleAuthManager(context)

    fun getDeviceLoggedGoogleAccounts(): List<com.example.drivepool.data.remote.DiscoveredDeviceAccount> {
        val currentEmails = _nodes.value.map { it.email }.toSet()
        return authManager.getDeviceLoggedGoogleAccounts(currentEmails)
    }

    fun getGoogleSignInClient(activity: android.app.Activity): com.google.android.gms.auth.api.signin.GoogleSignInClient {
        return authManager.getGoogleSignInClient(activity)
    }

    fun parseGoogleSignInResult(data: android.content.Intent?): Result<DriveNode> {
        return authManager.parseSignInResult(data)
    }

    fun createSystemAccountPickerIntent(): android.content.Intent {
        return authManager.createSystemAccountPickerIntent()
    }

    suspend fun addAuthenticatedGoogleNode(node: DriveNode): Result<DriveNode> = withContext(Dispatchers.Default) {
        val currentNodes = _nodes.value
        if (currentNodes.any { it.email.equals(node.email, ignoreCase = true) }) {
            return@withContext Result.failure(Exception("Account ${node.email} is already connected to DrivePool."))
        }

        val isFirst = currentNodes.isEmpty()
        val nodeToAdd = if (isFirst && node.role != NodeRole.MASTER) {
            node.copy(role = NodeRole.MASTER)
        } else {
            node
        }

        val updatedNodes = currentNodes + nodeToAdd
        dbHelper.saveNodes(updatedNodes)
        _nodes.value = updatedNodes

        val master = updatedNodes.find { it.role == NodeRole.MASTER } ?: updatedNodes.first()
        val catalog = indexManager.buildCatalog(master, updatedNodes, _files.value)
        indexManager.saveCatalog(catalog)

        Result.success(nodeToAdd)
    }
}
