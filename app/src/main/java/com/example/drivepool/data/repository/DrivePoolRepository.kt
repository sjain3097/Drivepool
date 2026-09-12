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
    suspend fun deleteFile(file: PoolFile): Result<Unit> = withContext(Dispatchers.Default) {
        val currentNodes = _nodes.value
        val updatedNodes = currentNodes.map { node ->
            if (node.id == file.physicalNodeId) {
                node.copy(usedBytes = maxOf(0L, node.usedBytes - file.sizeBytes))
            } else {
                node
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

        Result.success(Unit)
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
        }
        return result
    }

    /**
     * Deletes a local file from phone storage (frees phone memory).
     */
    suspend fun deleteLocalPhoneFile(file: com.example.drivepool.data.model.LocalPhoneFile): Boolean = withContext(Dispatchers.Default) {
        val deleted = phoneManager.deleteLocalFile(file.id)
        if (deleted) {
            _phoneFiles.value = phoneManager.getPhoneFiles()
        }
        deleted
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
