package com.example.drivepool.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivepool.data.model.FileCategory
import com.example.drivepool.data.model.LocalPhoneFile
import com.example.drivepool.ui.components.BulkDeleteConfirmationDialog
import com.example.drivepool.ui.components.BulkUploadConfirmationDialog
import com.example.drivepool.ui.components.FileIcon
import com.example.drivepool.ui.components.FileSelectionHeader
import com.example.drivepool.ui.components.FileThumbnail
import com.example.drivepool.ui.components.FileViewModeHeader
import com.example.drivepool.ui.components.PhoneFileGridCard
import com.example.drivepool.ui.components.PhoneFileRowCard
import com.example.drivepool.ui.components.StorageProgressBar
import com.example.drivepool.ui.viewmodel.DrivePoolUiState
import com.example.drivepool.ui.viewmodel.DrivePoolViewModel
import com.example.drivepool.ui.viewmodel.FileViewLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPhoneFilesScreen(
    state: DrivePoolUiState,
    viewModel: DrivePoolViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showBulkUploadDialog by remember { mutableStateOf(false) }
    var isStorageExpanded by remember { mutableStateOf(false) }
    val isSelectionMode = state.selectedPhoneFileIds.isNotEmpty()

    // Back button handling inside Phone Files Screen
    BackHandler(enabled = isSelectionMode) {
        viewModel.clearPhoneFileSelection()
    }

    BackHandler(enabled = !isSelectionMode && state.selectedPhoneFile != null) {
        viewModel.onPhoneFileSelected(null)
    }

    BackHandler(enabled = !isSelectionMode && state.selectedPhoneFile == null && state.phoneSubTab == com.example.drivepool.ui.viewmodel.PhoneSubTab.ORGANIZER && state.selectedPhoneCategoryFolder != null) {
        viewModel.onPhoneCategoryFolderSelected(null)
    }

    BackHandler(enabled = !isSelectionMode && state.selectedPhoneFile == null && state.isFolderViewMode && state.currentFolderResult?.parentPath != null) {
        viewModel.navigateUpFolder()
    }

    // Android System Document/Media Picker (Multiple File Support)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                var fileName = "Imported_File"
                var fileSize = 1024L * 1024L // Default 1 MB
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                viewModel.addPickedPhoneFile(
                    name = fileName,
                    size = if (fileSize > 0) fileSize else 500L * 1024,
                    mimeType = mimeType,
                    path = "/storage/emulated/0/Download/$fileName"
                )
            }
        }
    }

    // Android All Files Access Storage Permission Launcher
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkStoragePermission()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Sub-Tab Switcher: [ Files & Folders ] | [ Storage Organizer ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.phoneSubTab == com.example.drivepool.ui.viewmodel.PhoneSubTab.FILES,
                onClick = { viewModel.setPhoneSubTab(com.example.drivepool.ui.viewmodel.PhoneSubTab.FILES) },
                label = { Text("Files & Folders", fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = state.phoneSubTab == com.example.drivepool.ui.viewmodel.PhoneSubTab.ORGANIZER,
                onClick = { viewModel.setPhoneSubTab(com.example.drivepool.ui.viewmodel.PhoneSubTab.ORGANIZER) },
                label = { Text("Storage Organizer", fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )
        }

        if (state.phoneSubTab == com.example.drivepool.ui.viewmodel.PhoneSubTab.ORGANIZER) {
            PhoneOrganizerView(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
            )
        } else {
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Phone Storage Info Card (Default Collapsed into a Blue Bar)
                Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { isStorageExpanded = !isStorageExpanded },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Collapsed Bar Header (Always visible, sleek blue bar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Phone Storage",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    val info = state.deviceStorageInfo
                    Text(
                        text = " • ${info.formattedFree} free (${(100f - info.usagePercent).toInt()}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Pick", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        imageVector = if (isStorageExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isStorageExpanded) "Collapse Storage Info" else "Expand Storage Info",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Expandable Details (Smooth transition)
                AnimatedVisibility(visible = isStorageExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp, top = 2.dp)
                    ) {
                        val info = state.deviceStorageInfo
                        Text(
                            text = "${info.formattedFree} free of ${info.formattedTotal} • ${state.phoneFiles.size} Files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        StorageProgressBar(
                            usedPercent = info.usagePercent,
                            formattedUsed = info.formattedUsed,
                            formattedTotal = info.formattedTotal,
                            barHeight = 8.dp
                        )
                    }
                }
            }
        }

        // All Files Access Permission Prompt Banner
        if (!state.hasStoragePermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "All Files Access Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Grant storage permission to browse device folders and open local files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            try {
                                storagePermissionLauncher.launch(viewModel.getManageStorageIntent())
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                storagePermissionLauncher.launch(intent)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // View Mode Selector: Categorized Files vs Folder Explorer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !state.isFolderViewMode,
                onClick = { if (state.isFolderViewMode) viewModel.toggleFolderViewMode() },
                label = { Text("Categorized Files") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
            FilterChip(
                selected = state.isFolderViewMode,
                onClick = { if (!state.isFolderViewMode) viewModel.toggleFolderViewMode() },
                label = { Text("Folder Explorer") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }

        // Phone Search Bar
        OutlinedTextField(
            value = state.phoneSearchQuery,
            onValueChange = { viewModel.onPhoneSearchQueryChanged(it) },
            placeholder = { Text("Search phone files...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (state.phoneSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onPhoneSearchQueryChanged("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Upload Progress Indicator
        if (state.isUploading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.statusMessage ?: "Uploading to Google Drive cluster...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Live Upload Error / Status Notification Banner
        if (!state.isUploading && state.statusMessage != null) {
            val isError = state.statusMessage.contains("failed", ignoreCase = true) ||
                    state.statusMessage.contains("blocked", ignoreCase = true) ||
                    state.statusMessage.contains("error", ignoreCase = true)
            val isConsoleError = state.statusMessage.contains("Google Cloud Console", ignoreCase = true) ||
                    state.statusMessage.contains("UnregisteredOnApiConsole", ignoreCase = true)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isError)
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                    else
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (state.authConsentIntent != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap here to grant Google Drive permission",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { viewModel.retryAuthConsent() }
                            )
                        }
                        if (isConsoleError) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap here to view SHA-1 & setup instructions",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { viewModel.openAuthSetupDialog() }
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.clearStatusMessage() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (state.isFolderViewMode) {
            // Folder Explorer Navigation Bar
            val currentResult = state.currentFolderResult
            val displayPath = (state.currentFolderPath ?: "/storage/emulated/0")
                .replace("/storage/emulated/0", "Internal Storage")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateUpFolder() },
                        enabled = currentResult?.parentPath != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Up",
                            tint = if (currentResult?.parentPath != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }

                    Text(
                        text = displayPath,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = { viewModel.loadFolder(state.currentFolderPath) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (currentResult == null) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    viewModel.loadFolder(null)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (currentResult.folders.isEmpty() && currentResult.files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "This folder is empty",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (isSelectionMode) {
                    FileSelectionHeader(
                        selectedCount = state.selectedPhoneFileIds.size,
                        totalCount = currentResult.files.size,
                        onClearSelection = { viewModel.clearPhoneFileSelection() },
                        onSelectAll = {
                            if (state.selectedPhoneFileIds.size == currentResult.files.size) {
                                viewModel.clearPhoneFileSelection()
                            } else {
                                viewModel.selectAllPhoneFiles(currentResult.files.map { it.id })
                            }
                        },
                        onDeleteClick = { showBulkDeleteDialog = true },
                        onUploadClick = { showBulkUploadDialog = true }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentResult.folders, key = { "folder_${it.path}" }) { folder ->
                        FolderItemRow(
                            folder = folder,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.clearPhoneFileSelection()
                                }
                                viewModel.loadFolder(folder.path)
                            }
                        )
                    }
                    items(currentResult.files, key = { "file_${it.id}" }) { file ->
                        val isSelected = state.selectedPhoneFileIds.contains(file.id)
                        PhoneFileItem(
                            file = file,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.togglePhoneFileSelection(file.id)
                                } else {
                                    viewModel.onPhoneFileSelected(file)
                                }
                            },
                            onLongClick = {
                                viewModel.togglePhoneFileSelection(file.id)
                            },
                            onViewClick = { viewModel.previewPhoneFile(file, currentResult.files) },
                            onUploadClick = { viewModel.uploadLocalPhoneFile(file) }
                        )
                    }
                }
            }
        } else {
            // Phone Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FileCategory.values().forEach { category ->
                    val isSelected = state.phoneCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onPhoneCategorySelected(category) },
                        label = {
                            Text(
                                text = if (category == FileCategory.ALL) "All Phone Files" else category.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Phone Files List
            if (state.phoneFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (state.phoneSearchQuery.isNotEmpty()) "No files match \"${state.phoneSearchQuery}\"" else "No local files found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (isSelectionMode) {
                    FileSelectionHeader(
                        selectedCount = state.selectedPhoneFileIds.size,
                        totalCount = state.phoneFiles.size,
                        onClearSelection = { viewModel.clearPhoneFileSelection() },
                        onSelectAll = {
                            if (state.selectedPhoneFileIds.size == state.phoneFiles.size) {
                                viewModel.clearPhoneFileSelection()
                            } else {
                                viewModel.selectAllPhoneFiles(state.phoneFiles.map { it.id })
                            }
                        },
                        onDeleteClick = { showBulkDeleteDialog = true },
                        onUploadClick = { showBulkUploadDialog = true }
                    )
                } else {
                    FileViewModeHeader(
                        title = if (state.phoneSearchQuery.isNotEmpty()) "Search Results" else "Phone Files",
                        count = state.phoneFiles.size,
                        currentLayout = state.fileViewLayout,
                        onLayoutChanged = { viewModel.setFileViewLayout(it) }
                    )
                }

                when (state.fileViewLayout) {
                    FileViewLayout.VERTICAL_LIST -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.phoneFiles, key = { it.id }) { file ->
                                val isSelected = state.selectedPhoneFileIds.contains(file.id)
                                PhoneFileItem(
                                    file = file,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.togglePhoneFileSelection(file.id)
                                        } else {
                                            viewModel.onPhoneFileSelected(file)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.togglePhoneFileSelection(file.id)
                                    },
                                    onViewClick = { viewModel.previewPhoneFile(file, state.phoneFiles) },
                                    onUploadClick = { viewModel.uploadLocalPhoneFile(file) }
                                )
                            }
                        }
                    }
                    FileViewLayout.HORIZONTAL_GRID -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.phoneFiles, key = { it.id }) { file ->
                                val isSelected = state.selectedPhoneFileIds.contains(file.id)
                                PhoneFileGridCard(
                                    file = file,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel.togglePhoneFileSelection(file.id)
                                        } else {
                                            viewModel.previewPhoneFile(file, state.phoneFiles)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.togglePhoneFileSelection(file.id)
                                    }
                                )
                            }
                        }
                    }
                    FileViewLayout.HORIZONTAL_ROW -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Text(
                                text = "Horizontal Scroll Gallery",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.phoneFiles, key = { "row_${it.id}" }) { file ->
                                    val isSelected = state.selectedPhoneFileIds.contains(file.id)
                                    PhoneFileRowCard(
                                        file = file,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.togglePhoneFileSelection(file.id)
                                            } else {
                                                viewModel.previewPhoneFile(file, state.phoneFiles)
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.togglePhoneFileSelection(file.id)
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(state.phoneFiles, key = { "sub_${it.id}" }) { file ->
                                    val isSelected = state.selectedPhoneFileIds.contains(file.id)
                                    PhoneFileItem(
                                        file = file,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.togglePhoneFileSelection(file.id)
                                            } else {
                                                viewModel.onPhoneFileSelected(file)
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.togglePhoneFileSelection(file.id)
                                        },
                                        onViewClick = { viewModel.previewPhoneFile(file, state.phoneFiles) },
                                        onUploadClick = { viewModel.uploadLocalPhoneFile(file) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
    }

    // Phone File Details Bottom Sheet
    state.selectedPhoneFile?.let { file ->
        val activeContextList = state.currentFolderResult?.files ?: state.phoneFiles
        PhoneFileDetailsSheet(
            file = file,
            onDismiss = { viewModel.onPhoneFileSelected(null) },
            onView = {
                viewModel.previewPhoneFile(file, activeContextList)
                viewModel.onPhoneFileSelected(null)
            },
            onShare = {
                viewModel.sharePhoneFile(context, file)
                viewModel.onPhoneFileSelected(null)
            },
            onUpload = {
                viewModel.uploadLocalPhoneFile(file)
                viewModel.onPhoneFileSelected(null)
            },
            onDelete = {
                viewModel.deletePhoneFile(file)
                viewModel.onPhoneFileSelected(null)
            }
        )
    }

    // Bulk Delete Confirmation Dialog
    if (showBulkDeleteDialog) {
        BulkDeleteConfirmationDialog(
            count = state.selectedPhoneFileIds.size,
            isPhoneStorage = true,
            onDismiss = { showBulkDeleteDialog = false },
            onConfirm = {
                showBulkDeleteDialog = false
                viewModel.deleteSelectedPhoneFiles()
            }
        )
    }

    // Bulk Upload Confirmation Dialog
    if (showBulkUploadDialog) {
        val allKnownFiles = state.phoneFiles + (state.currentFolderResult?.files ?: emptyList())
        val selectedFiles = allKnownFiles.filter { it.id in state.selectedPhoneFileIds }.distinctBy { it.id }
        val totalSize = selectedFiles.sumOf { it.sizeBytes }

        BulkUploadConfirmationDialog(
            count = state.selectedPhoneFileIds.size,
            totalSizeBytes = totalSize,
            onDismiss = { showBulkUploadDialog = false },
            onConfirm = {
                showBulkUploadDialog = false
                viewModel.uploadSelectedPhoneFiles()
            }
        )
    }
}

@Composable
private fun FolderItemRow(
    folder: com.example.drivepool.data.local.PhoneFolderItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFEF7E0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFFF29900),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${folder.itemCount} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open folder",
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneFileItem(
    file: LocalPhoneFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onViewClick: () -> Unit,
    onUploadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 4.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            FileThumbnail(file = file, size = 48.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${file.formattedSize} • ${file.formattedDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = file.path.substringAfter("0/"),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isSelectionMode) {
                Spacer(modifier = Modifier.width(6.dp))

                // Direct View / Open Button
                FilledTonalButton(
                    onClick = onViewClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = "View",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Upload Status / Button
                if (file.isUploadedToPool) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Uploaded",
                            tint = Color(0xFF137333),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Backed Up",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF137333)
                        )
                    }
                } else {
                    Button(
                        onClick = onUploadClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload to DrivePool",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Upload", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneFileDetailsSheet(
    file: LocalPhoneFile,
    onDismiss: () -> Unit,
    onView: () -> Unit,
    onShare: () -> Unit,
    onUpload: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileThumbnail(file = file, size = 56.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${file.formattedSize} • ${file.formattedDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Phone Storage Location",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = file.path,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "MIME Type",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = file.mimeType,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "DrivePool Cloud Status",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (file.isUploadedToPool) "Backed up on ${file.uploadedToNodeEmail}" else "Not yet uploaded to DrivePool cluster",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (file.isUploadedToPool) Color(0xFF137333) else Color(0xFFE37400)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Actions: Open / View File and Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onView()
                    },
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open / View", fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        onShare()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onUpload,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (file.isUploadedToPool) "Re-Upload" else "Upload to Drive")
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete from Phone")
                }
            }
        }
    }
}
