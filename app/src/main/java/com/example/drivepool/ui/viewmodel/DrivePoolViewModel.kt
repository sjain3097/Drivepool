package com.example.drivepool.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivepool.data.balancer.AllocationDecision
import com.example.drivepool.data.model.ClusterStats
import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.FileCategory
import com.example.drivepool.data.model.LocalPhoneFile
import com.example.drivepool.data.model.PoolFile
import com.example.drivepool.data.remote.DriveConsentRequiredException
import com.example.drivepool.data.remote.DriveUnregisteredConsoleException
import com.example.drivepool.data.repository.DrivePoolRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StorageViewMode {
    CLOUD_POOL,
    PHONE_STORAGE
}

data class DrivePoolUiState(
    val activeStorageMode: StorageViewMode = StorageViewMode.CLOUD_POOL,
    val nodes: List<DriveNode> = emptyList(),
    val files: List<PoolFile> = emptyList(),
    val phoneFiles: List<LocalPhoneFile> = emptyList(),
    val clusterStats: ClusterStats = ClusterStats(),
    val searchQuery: String = "",
    val selectedCategory: FileCategory = FileCategory.ALL,
    val phoneSearchQuery: String = "",
    val phoneCategory: FileCategory = FileCategory.ALL,
    val selectedFileForDetails: PoolFile? = null,
    val selectedPhoneFile: LocalPhoneFile? = null,
    val selectedCategoryFolder: FileCategory? = null,
    val organizerInsights: com.example.drivepool.data.model.OrganizerInsights = com.example.drivepool.data.model.OrganizerInsights(),
    val isUploading: Boolean = false,
    val isTransferring: Boolean = false,
    val statusMessage: String? = null,
    val lastAllocation: AllocationDecision? = null,
    val discoveredAccounts: List<com.example.drivepool.data.remote.DiscoveredDeviceAccount> = emptyList(),
    val deviceStorageInfo: com.example.drivepool.data.local.DeviceStorageInfo = com.example.drivepool.data.local.DeviceStorageInfo(512L * 1024 * 1024 * 1024, 142L * 1024 * 1024 * 1024, 370L * 1024 * 1024 * 1024, 512),
    val showUploadDialog: Boolean = false,
    val showAddAccountDialog: Boolean = false,
    val showFailoverDialog: Boolean = false,
    val showSimulateMasterFullDialog: Boolean = false,
    val authConsentIntent: Intent? = null,
    val showAuthSetupDialog: Boolean = false,
    val authErrorMessage: String? = null,
    val viewingTarget: com.example.drivepool.ui.screens.viewer.ViewingFileTarget? = null,
    val hasStoragePermission: Boolean = false,
    val currentFolderPath: String? = null,
    val currentFolderResult: com.example.drivepool.data.local.FolderContentResult? = null,
    val isFolderViewMode: Boolean = false,
    val isViewerLoading: Boolean = false
)

class DrivePoolViewModel(
    private val repository: DrivePoolRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DrivePoolUiState())
    val uiState: StateFlow<DrivePoolUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.nodes.collect { currentNodes ->
                _uiState.update { it.copy(nodes = currentNodes) }
                refreshDiscoveredAccounts()
            }
        }
        viewModelScope.launch {
            repository.filteredFiles.collect { currentFiles ->
                _uiState.update { it.copy(files = currentFiles) }
            }
        }
        viewModelScope.launch {
            repository.filteredPhoneFiles.collect { currentPhoneFiles ->
                _uiState.update { it.copy(phoneFiles = currentPhoneFiles) }
            }
        }
        viewModelScope.launch {
            repository.deviceStorageInfo.collect { storageInfo ->
                _uiState.update { it.copy(deviceStorageInfo = storageInfo) }
            }
        }
        viewModelScope.launch {
            repository.clusterStats.collect { currentStats ->
                _uiState.update { it.copy(clusterStats = currentStats) }
            }
        }
        viewModelScope.launch {
            repository.searchQuery.collect { query ->
                _uiState.update { it.copy(searchQuery = query) }
            }
        }
        viewModelScope.launch {
            repository.selectedCategory.collect { category ->
                _uiState.update { it.copy(selectedCategory = category) }
            }
        }
        viewModelScope.launch {
            repository.phoneSearchQuery.collect { query ->
                _uiState.update { it.copy(phoneSearchQuery = query) }
            }
        }
        viewModelScope.launch {
            repository.phoneCategory.collect { category ->
                _uiState.update { it.copy(phoneCategory = category) }
            }
        }
        viewModelScope.launch {
            repository.organizerInsights.collect { insights ->
                _uiState.update { it.copy(organizerInsights = insights) }
            }
        }
        checkStoragePermission()
    }

    fun onCategoryFolderSelected(category: FileCategory?) {
        _uiState.update { it.copy(selectedCategoryFolder = category) }
    }

    fun runAutoOrganize() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Auto-organizing files into structured virtual folders...") }
            val count = repository.autoOrganizeFiles()
            _uiState.update { it.copy(statusMessage = "Successfully organized $count files into category folders!") }
        }
    }

    fun setStorageViewMode(mode: StorageViewMode) {
        _uiState.update { it.copy(activeStorageMode = mode) }
    }

    fun onSearchQueryChanged(query: String) {
        repository.searchQuery.value = query
    }

    fun onCategorySelected(category: FileCategory) {
        repository.selectedCategory.value = category
    }

    fun onPhoneSearchQueryChanged(query: String) {
        repository.phoneSearchQuery.value = query
    }

    fun onPhoneCategorySelected(category: FileCategory) {
        repository.phoneCategory.value = category
    }

    fun onFileSelected(file: PoolFile?) {
        _uiState.update { it.copy(selectedFileForDetails = file) }
    }

    fun onPhoneFileSelected(file: LocalPhoneFile?) {
        _uiState.update { it.copy(selectedPhoneFile = file) }
    }

    fun setUploadDialogVisible(visible: Boolean) {
        _uiState.update {
            it.copy(
                showUploadDialog = visible,
                lastAllocation = if (!visible) null else it.lastAllocation
            )
        }
    }

    fun setAddAccountDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showAddAccountDialog = visible) }
    }

    fun setFailoverDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showFailoverDialog = visible) }
    }

    fun setSimulateMasterFullDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showSimulateMasterFullDialog = visible) }
    }

    private var pendingUploadFile: LocalPhoneFile? = null
    private var pendingVirtualUpload: Triple<String, Long, String>? = null
    private var pendingPreviewFile: PoolFile? = null

    private fun handleUploadFailure(error: Throwable) {
        val msg = error.message ?: "Upload failed"

        // Extract consent intent from any layer of the exception chain
        val consentIntent = when {
            error is DriveConsentRequiredException -> error.consentIntent
            error.cause is DriveConsentRequiredException -> (error.cause as DriveConsentRequiredException).consentIntent
            error is com.google.android.gms.auth.UserRecoverableAuthException -> error.intent
            error.cause is com.google.android.gms.auth.UserRecoverableAuthException -> (error.cause as com.google.android.gms.auth.UserRecoverableAuthException).intent
            else -> null
        }

        if (consentIntent != null) {
            _uiState.update {
                it.copy(
                    isUploading = false,
                    authConsentIntent = consentIntent,
                    statusMessage = "Google Drive authorization required. Please approve access."
                )
            }
            return
        }

        val isUnregistered = error is DriveUnregisteredConsoleException ||
                error.cause is DriveUnregisteredConsoleException ||
                msg.contains("UnregisteredOnApiConsole", ignoreCase = true)

        if (isUnregistered) {
            _uiState.update {
                it.copy(
                    isUploading = false,
                    showAuthSetupDialog = true,
                    authErrorMessage = msg,
                    statusMessage = "Google Drive upload blocked: App not registered in Google Cloud Console."
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isUploading = false,
                    statusMessage = "Upload failed: $msg"
                )
            }
        }
    }

    private fun handlePreviewFailure(error: Throwable) {
        val msg = error.message ?: "Could not open file"

        val consentIntent = when {
            error is DriveConsentRequiredException -> error.consentIntent
            error.cause is DriveConsentRequiredException -> (error.cause as DriveConsentRequiredException).consentIntent
            error is com.google.android.gms.auth.UserRecoverableAuthException -> error.intent
            error.cause is com.google.android.gms.auth.UserRecoverableAuthException -> (error.cause as com.google.android.gms.auth.UserRecoverableAuthException).intent
            else -> null
        }

        if (consentIntent != null) {
            _uiState.update {
                it.copy(
                    authConsentIntent = consentIntent,
                    statusMessage = "Google Drive authorization required to view file. Please approve access."
                )
            }
            return
        }

        val isUnregistered = error is DriveUnregisteredConsoleException ||
                error.cause is DriveUnregisteredConsoleException ||
                msg.contains("UnregisteredOnApiConsole", ignoreCase = true)

        if (isUnregistered) {
            _uiState.update {
                it.copy(
                    showAuthSetupDialog = true,
                    authErrorMessage = msg,
                    statusMessage = "Google Drive download blocked: App not registered in Google Cloud Console."
                )
            }
        } else {
            val userMsg = when {
                error is java.io.FileNotFoundException || msg.contains("404") || msg.contains("notFound", ignoreCase = true) ->
                    "File not found on Google Drive. It may have been deleted or moved remotely."
                msg.contains("403") || msg.contains("Access denied", ignoreCase = true) ->
                    "Access denied by Google Drive. Check permissions for this account."
                else -> "Could not open file: ${msg.take(120)}"
            }
            _uiState.update {
                it.copy(
                    statusMessage = userMsg
                )
            }
        }
    }

    fun dismissAuthSetupDialog() {
        _uiState.update { it.copy(showAuthSetupDialog = false) }
    }

    fun openAuthSetupDialog() {
        _uiState.update { it.copy(showAuthSetupDialog = true) }
    }

    fun retryAuthConsent() {
        val currentIntent = _uiState.value.authConsentIntent
        if (currentIntent != null) {
            _uiState.update { it.copy(authConsentIntent = null) }
            _uiState.update { it.copy(authConsentIntent = currentIntent) }
        }
    }

    fun onConsentCompleted(granted: Boolean) {
        _uiState.update { it.copy(authConsentIntent = null) }
        if (granted) {
            _uiState.update { it.copy(statusMessage = "Google Drive access granted!") }
            pendingPreviewFile?.let { file ->
                val toPreview = file
                pendingPreviewFile = null
                previewPoolFile(toPreview)
            } ?: pendingUploadFile?.let { file ->
                val toUpload = file
                pendingUploadFile = null
                uploadLocalPhoneFile(toUpload)
            } ?: pendingVirtualUpload?.let { (name, size, mime) ->
                pendingVirtualUpload = null
                uploadFile(name, size, mime)
            }
        } else {
            _uiState.update { it.copy(statusMessage = "Google Drive permission was denied.") }
            pendingPreviewFile = null
            pendingUploadFile = null
            pendingVirtualUpload = null
        }
    }

    fun checkStoragePermission() {
        val granted = repository.hasStoragePermission()
        _uiState.update { it.copy(hasStoragePermission = granted) }
        if (granted && _uiState.value.isFolderViewMode && _uiState.value.currentFolderResult == null) {
            loadFolder(null)
        }
    }

    fun getManageStorageIntent(): Intent = repository.getManageStorageIntent()

    fun toggleFolderViewMode() {
        val next = !_uiState.value.isFolderViewMode
        _uiState.update { it.copy(isFolderViewMode = next) }
        if (next && _uiState.value.currentFolderResult == null) {
            loadFolder(null)
        }
    }

    fun loadFolder(path: String?) {
        viewModelScope.launch {
            val result = repository.listFolderContents(path)
            _uiState.update {
                it.copy(
                    currentFolderPath = result.currentPath,
                    currentFolderResult = result
                )
            }
        }
    }

    fun navigateUpFolder() {
        val parent = _uiState.value.currentFolderResult?.parentPath
        loadFolder(parent)
    }

    fun previewPoolFile(file: PoolFile) {
        pendingPreviewFile = file
        viewModelScope.launch {
            _uiState.update { it.copy(isViewerLoading = true, statusMessage = "Loading \"${file.name}\"...") }
            val result = repository.prepareFileForViewing(file)
            result.onSuccess { localFile ->
                pendingPreviewFile = null
                val isVirtual = file.remoteDriveFileId.startsWith("virtual_", ignoreCase = true) ||
                        file.remoteDriveFileId.startsWith("mock_", ignoreCase = true) ||
                        file.remoteDriveFileId.isBlank()
                val sourceDesc = if (isVirtual) {
                    "Virtual Cluster Replica (${file.physicalNodeEmail})"
                } else {
                    "Google Drive (${file.physicalNodeEmail})"
                }
                _uiState.update {
                    it.copy(
                        isViewerLoading = false,
                        statusMessage = null,
                        viewingTarget = com.example.drivepool.ui.screens.viewer.ViewingFileTarget(
                            name = file.name,
                            mimeType = file.mimeType,
                            sizeBytes = file.sizeBytes,
                            file = localFile,
                            sourceDescription = sourceDesc
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(isViewerLoading = false) }
                handlePreviewFailure(err)
            }
        }
    }

    fun previewPhoneFile(file: LocalPhoneFile) {
        viewModelScope.launch {
            val result = repository.preparePhoneFileForViewing(file)
            result.onSuccess { localFile ->
                _uiState.update {
                    it.copy(
                        viewingTarget = com.example.drivepool.ui.screens.viewer.ViewingFileTarget(
                            name = file.name,
                            mimeType = file.mimeType,
                            sizeBytes = file.sizeBytes,
                            file = localFile,
                            sourceDescription = "Phone Internal Storage"
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusMessage = "Could not resolve file: ${err.message}")
                }
            }
        }
    }

    fun dismissFileViewer() {
        _uiState.update { it.copy(viewingTarget = null) }
    }

    fun uploadFile(name: String, sizeBytes: Long, mimeType: String) {
        pendingVirtualUpload = Triple(name, sizeBytes, mimeType)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    statusMessage = "Analyzing cluster storage & balancing..."
                )
            }
            val result = repository.uploadFile(name, sizeBytes, mimeType)

            result.onSuccess { decision ->
                pendingVirtualUpload = null
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        lastAllocation = decision,
                        statusMessage = "Allocated to ${decision.targetNode.email} (${decision.explanation})"
                    )
                }
            }.onFailure { error ->
                handleUploadFailure(error)
            }
        }
    }

    fun uploadLocalPhoneFile(file: LocalPhoneFile) {
        pendingUploadFile = file
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    statusMessage = "Balancing & uploading \"${file.name}\" to Google Drive cluster..."
                )
            }
            val result = repository.uploadLocalFileToPool(file)

            result.onSuccess { decision ->
                pendingUploadFile = null
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        lastAllocation = decision,
                        statusMessage = "Uploaded ${file.name} to ${decision.targetNode.email}!"
                    )
                }
            }.onFailure { error ->
                handleUploadFailure(error)
            }
        }
    }

    fun deletePhoneFile(file: LocalPhoneFile) {
        viewModelScope.launch {
            val deleted = repository.deleteLocalPhoneFile(file)
            if (deleted) {
                _uiState.update {
                    it.copy(
                        selectedPhoneFile = null,
                        statusMessage = "Deleted ${file.name} from phone storage."
                    )
                }
            }
        }
    }

    fun addPickedPhoneFile(name: String, size: Long, mimeType: String, path: String) {
        viewModelScope.launch {
            val file = repository.addPickedPhoneFile(name, size, mimeType, path)
            _uiState.update {
                it.copy(statusMessage = "Imported \"${file.name}\" from device.")
            }
        }
    }

    fun rebalanceFile(file: PoolFile, targetNodeId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTransferring = true,
                    statusMessage = "Transferring ${file.name} to new storage node..."
                )
            }
            val result = repository.rebalanceFile(file, targetNodeId)

            result.onSuccess {
                val updated = repository.files.value.find { it.id == file.id }
                _uiState.update {
                    it.copy(
                        isTransferring = false,
                        selectedFileForDetails = updated,
                        statusMessage = "File migrated successfully! Master index updated."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isTransferring = false,
                        statusMessage = "Transfer failed: ${error.message}"
                    )
                }
            }
        }
    }

    fun deleteFile(file: PoolFile) {
        viewModelScope.launch {
            val result = repository.deleteFile(file)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        selectedFileForDetails = null,
                        statusMessage = "Deleted ${file.name}. Storage reclaimed on ${file.physicalNodeEmail}."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = "Deletion failed: ${error.message}")
                }
            }
        }
    }

    fun triggerMasterFailover() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Conducting dynamic master election...") }
            val result = repository.triggerMasterFailover()
            result.onSuccess { election ->
                _uiState.update {
                    it.copy(
                        showFailoverDialog = false,
                        statusMessage = election.reason
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = "Failover failed: ${error.message}")
                }
            }
        }
    }

    fun simulateFillMasterDrive(bytesRemaining: Long) {
        viewModelScope.launch {
            repository.simulateFillMasterDrive(bytesRemaining)
            _uiState.update {
                it.copy(
                    showSimulateMasterFullDialog = false,
                    statusMessage = "Master capacity simulated! Free space reduced to ${DriveNode.formatBytes(bytesRemaining)}."
                )
            }
        }
    }

    fun addNewAccount(email: String, displayName: String) {
        viewModelScope.launch {
            val result = repository.addNewAccount(email, displayName)
            result.onSuccess { node ->
                _uiState.update {
                    it.copy(
                        showAddAccountDialog = false,
                        statusMessage = "Added ${node.email}! Pooled +15 GB to cluster."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message)
                }
            }
        }
    }

    fun resetCluster() {
        viewModelScope.launch {
            repository.resetCluster()
            _uiState.update {
                it.copy(
                    selectedFileForDetails = null,
                    statusMessage = "Cluster reset to default 3-node demo configuration."
                )
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun refreshDiscoveredAccounts() {
        val discovered = repository.getDeviceLoggedGoogleAccounts()
        _uiState.update { it.copy(discoveredAccounts = discovered) }
    }

    fun getGoogleSignInClient(activity: android.app.Activity): com.google.android.gms.auth.api.signin.GoogleSignInClient {
        return repository.getGoogleSignInClient(activity)
    }

    fun onGoogleSignInResult(data: android.content.Intent?) {
        viewModelScope.launch {
            val result = repository.parseGoogleSignInResult(data)
            result.onSuccess { node ->
                val addResult = repository.addAuthenticatedGoogleNode(node)
                addResult.onSuccess {
                    refreshDiscoveredAccounts()
                    _uiState.update {
                        it.copy(
                            showAddAccountDialog = false,
                            statusMessage = "Google Account ${node.email} signed in! +15 GB pooled."
                        )
                    }
                }.onFailure { error ->
                    _uiState.update { it.copy(statusMessage = error.message) }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(statusMessage = "Google Sign-In: ${error.message}") }
            }
        }
    }

    fun connectDiscoveredAccount(account: com.example.drivepool.data.remote.DiscoveredDeviceAccount) {
        viewModelScope.launch {
            val result = repository.addNewAccount(account.email, account.name)
            result.onSuccess { node ->
                refreshDiscoveredAccounts()
                _uiState.update {
                    it.copy(
                        showAddAccountDialog = false,
                        statusMessage = "Connected ${node.email} from device accounts (+15 GB free space)!"
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(statusMessage = error.message) }
            }
        }
    }
}
