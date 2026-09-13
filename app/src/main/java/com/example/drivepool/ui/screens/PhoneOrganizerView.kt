package com.example.drivepool.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivepool.data.model.CloudOffloadCandidate
import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.FileCategory
import com.example.drivepool.data.model.LocalPhoneFile
import com.example.drivepool.data.model.PhoneCategorySummary
import com.example.drivepool.data.model.PhoneDuplicateGroup
import com.example.drivepool.ui.components.FileIcon
import com.example.drivepool.ui.components.FileThumbnail
import com.example.drivepool.ui.components.PhoneFileOptionsMenu
import com.example.drivepool.ui.viewmodel.DrivePoolUiState
import com.example.drivepool.ui.viewmodel.DrivePoolViewModel

@Composable
fun PhoneOrganizerView(
    state: DrivePoolUiState,
    viewModel: DrivePoolViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedCategory = state.selectedPhoneCategoryFolder

    if (selectedCategory != null) {
        // Detailed Category Folder View for Phone Storage
        PhoneCategoryFolderDetailView(
            category = selectedCategory,
            files = state.phoneFiles.filter { it.category == selectedCategory },
            onBack = { viewModel.onPhoneCategoryFolderSelected(null) },
            onFileClick = { file, categoryFiles ->
                viewModel.previewPhoneFile(file, categoryFiles)
            },
            onDetails = { viewModel.onPhoneFileSelected(it) },
            onShare = { viewModel.sharePhoneFile(context, it) },
            onUploadToCloud = { viewModel.uploadLocalPhoneFile(it) },
            onDelete = { viewModel.deletePhoneFile(it) },
            onDeleteMultiple = { viewModel.deletePhoneFiles(it) },
            modifier = modifier
        )
    } else {
        // Main Phone Storage Organizer Hub
        PhoneOrganizerMainView(
            state = state,
            viewModel = viewModel,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneOrganizerMainView(
    state: DrivePoolUiState,
    viewModel: DrivePoolViewModel,
    modifier: Modifier = Modifier
) {
    val insights = state.phoneOrganizerInsights
    val storageInfo = state.deviceStorageInfo
    val context = LocalContext.current

    // Selection & Deletion state for Largest Files
    var isSelectingLargestFiles by remember { mutableStateOf(false) }
    var selectedLargestFileIds by remember { mutableStateOf(setOf<String>()) }
    var fileToDeleteSingle by remember { mutableStateOf<LocalPhoneFile?>(null) }
    var showBulkDeleteLargestDialog by remember { mutableStateOf(false) }

    // Intercept back button when in selection mode
    BackHandler(enabled = isSelectingLargestFiles) {
        isSelectingLargestFiles = false
        selectedLargestFileIds = emptySet()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Phone Storage Organizer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Analyze on-device storage, clean duplicates & offload to cloud",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Device Physical Storage Capacity Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Device Storage Overview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${storageInfo.formattedUsed} / ${storageInfo.formattedTotal}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Storage usage gauge / progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(storageInfo.usagePercent / 100f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (storageInfo.usagePercent > 85f) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${storageInfo.formattedFree} Available",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${String.format("%.1f", storageInfo.usagePercent)}% used",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Breakdown by scanned categories
                insights.categorySummaries.forEach { summary ->
                    PhoneCategoryProgressRow(summary = summary)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        // 2. Cloud Offload Assistant (files safely mirrored in CloudPool)
        if (insights.offloadCandidates.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE6F4EA)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = Color(0xFF137333),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Safe Cloud Offload Available",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF137333)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = insights.formattedOffloadSavings,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF137333)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "${insights.offloadCandidates.size} files are already safely stored in your DrivePool cloud. You can delete them from device storage to free up space.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF0D5325)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Show top offload candidates
                    insights.offloadCandidates.take(3).forEach { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${candidate.phoneFile.name}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF0D5325)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = candidate.formattedSize,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF137333)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { fileToDeleteSingle = candidate.phoneFile },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete local copy",
                                    tint = Color(0xFFC5221F),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Duplicate Phone Files Cleaner
        if (insights.duplicateCandidates.isNotEmpty()) {
            val totalDupSavings = insights.duplicateCandidates.sumOf { it.potentialSavingsBytes }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFEF7E0)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = Color(0xFFB06000),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Duplicate Files on Device",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB06000)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = DriveNode.formatBytes(totalDupSavings),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFB06000)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Found ${insights.duplicateCandidates.size} duplicate file sets taking unnecessary space on your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5D3E00)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    insights.duplicateCandidates.take(3).forEach { dup ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• \"${dup.fileName}\" (${dup.files.size} copies)",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                                color = Color(0xFF5D3E00)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = dup.formattedSavings,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB06000)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Delete duplicate copy (keep first, delete subsequent)
                            IconButton(
                                onClick = {
                                    if (dup.files.size > 1) {
                                        fileToDeleteSingle = dup.files.last()
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clean duplicate",
                                    tint = Color(0xFFC5221F),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Categories & Local Folders
        Text(
            text = "Storage Categories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        insights.categorySummaries.forEach { summary ->
            PhoneCategoryCard(
                summary = summary,
                onClick = { viewModel.onPhoneCategoryFolderSelected(summary.category) }
            )
        }

        // 5. Largest Files Consuming Space (with multi-select & delete options)
        if (insights.largestFiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            // Section Header with Multi-Select Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Largest Files on Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isSelectingLargestFiles) {
                        val selectedBytes = insights.largestFiles
                            .filter { it.id in selectedLargestFileIds }
                            .sumOf { it.sizeBytes }
                        Text(
                            text = "${selectedLargestFileIds.size} of ${insights.largestFiles.size} selected (${DriveNode.formatBytes(selectedBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isSelectingLargestFiles) {
                        FilledTonalButton(
                            onClick = {
                                isSelectingLargestFiles = true
                                selectedLargestFileIds = emptySet()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        TextButton(
                            onClick = {
                                if (selectedLargestFileIds.size == insights.largestFiles.size) {
                                    selectedLargestFileIds = emptySet()
                                } else {
                                    selectedLargestFileIds = insights.largestFiles.map { it.id }.toSet()
                                }
                            }
                        ) {
                            Text(
                                if (selectedLargestFileIds.size == insights.largestFiles.size) "Deselect"
                                else "Select All"
                            )
                        }
                        IconButton(
                            onClick = {
                                isSelectingLargestFiles = false
                                selectedLargestFileIds = emptySet()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel selection"
                            )
                        }
                    }
                }
            }

            // Selection Action Bar (when 1 or more files are selected)
            if (isSelectingLargestFiles && selectedLargestFileIds.isNotEmpty()) {
                val selectedFiles = insights.largestFiles.filter { it.id in selectedLargestFileIds }
                val totalSelectedBytes = selectedFiles.sumOf { it.sizeBytes }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedLargestFileIds.size} files (${DriveNode.formatBytes(totalSelectedBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Bulk Upload Button
                            FilledTonalButton(
                                onClick = {
                                    selectedFiles.forEach { viewModel.uploadLocalPhoneFile(it) }
                                    isSelectingLargestFiles = false
                                    selectedLargestFileIds = emptySet()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload", style = MaterialTheme.typography.labelSmall)
                            }

                            // Bulk Delete Button
                            Button(
                                onClick = { showBulkDeleteLargestDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Delete (${selectedLargestFileIds.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Largest File Cards
            insights.largestFiles.forEach { file ->
                val isSelected = file.id in selectedLargestFileIds
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (isSelectingLargestFiles) {
                                    selectedLargestFileIds = if (isSelected) {
                                        selectedLargestFileIds - file.id
                                    } else {
                                        selectedLargestFileIds + file.id
                                    }
                                } else {
                                    viewModel.previewPhoneFile(file, insights.largestFiles)
                                }
                            },
                            onLongClick = {
                                if (!isSelectingLargestFiles) {
                                    isSelectingLargestFiles = true
                                    selectedLargestFileIds = setOf(file.id)
                                }
                            }
                        ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectingLargestFiles) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = if (isSelected) "Selected" else "Not selected",
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        FileThumbnail(file = file, size = 42.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = file.formattedDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = file.formattedSize,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!isSelectingLargestFiles) {
                            PhoneFileOptionsMenu(
                                file = file,
                                onOpen = { viewModel.previewPhoneFile(file, insights.largestFiles) },
                                onDetails = { viewModel.onPhoneFileSelected(file) },
                                onShare = { viewModel.sharePhoneFile(context, file) },
                                onUpload = { viewModel.uploadLocalPhoneFile(file) },
                                onDelete = { fileToDeleteSingle = file }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Single File Delete Confirmation Dialog
        fileToDeleteSingle?.let { file ->
            AlertDialog(
                onDismissRequest = { fileToDeleteSingle = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "Delete File?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to permanently delete \"${file.name}\" (${file.formattedSize}) from your phone's internal storage? This action cannot be undone."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val f = file
                            fileToDeleteSingle = null
                            viewModel.deletePhoneFile(f)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete File", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { fileToDeleteSingle = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Bulk Delete Confirmation Dialog for Largest Files
        if (showBulkDeleteLargestDialog && selectedLargestFileIds.isNotEmpty()) {
            val filesToDelete = insights.largestFiles.filter { it.id in selectedLargestFileIds }
            val totalSize = filesToDelete.sumOf { it.sizeBytes }
            AlertDialog(
                onDismissRequest = { showBulkDeleteLargestDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "Delete ${filesToDelete.size} Files?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to permanently delete ${filesToDelete.size} largest files (${DriveNode.formatBytes(totalSize)}) from phone storage? This action cannot be undone."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBulkDeleteLargestDialog = false
                            viewModel.deletePhoneFiles(filesToDelete)
                            isSelectingLargestFiles = false
                            selectedLargestFileIds = emptySet()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete ${filesToDelete.size} Files", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showBulkDeleteLargestDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun PhoneCategoryProgressRow(summary: PhoneCategorySummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = summary.category.name.lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(90.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(summary.percentageOfTotal / 100f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(getPhoneCategoryColor(summary.category))
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = summary.formattedTotalSize,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(55.dp)
        )
    }
}

@Composable
private fun PhoneCategoryCard(
    summary: PhoneCategorySummary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(getPhoneCategoryColor(summary.category).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                FileIcon(
                    category = summary.category,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.category.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${summary.fileCount} files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = summary.formattedTotalSize,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneCategoryFolderDetailView(
    category: FileCategory,
    files: List<LocalPhoneFile>,
    onBack: () -> Unit,
    onFileClick: (LocalPhoneFile, List<LocalPhoneFile>) -> Unit,
    onDetails: (LocalPhoneFile) -> Unit,
    onShare: (LocalPhoneFile) -> Unit,
    onUploadToCloud: (LocalPhoneFile) -> Unit,
    onDelete: (LocalPhoneFile) -> Unit,
    onDeleteMultiple: (List<LocalPhoneFile>) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files
        else files.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    val totalBytes = remember(files) { files.sumOf { it.sizeBytes } }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFileIds by remember { mutableStateOf(setOf<String>()) }
    var fileToDeleteSingle by remember { mutableStateOf<LocalPhoneFile?>(null) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedFileIds = emptySet()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Navigation & Title Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to Organizer"
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isSelectionMode) "${selectedFileIds.size} of ${filteredFiles.size} selected"
                    else "${files.size} files • ${DriveNode.formatBytes(totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Selection Controls
            if (!isSelectionMode) {
                FilledTonalButton(
                    onClick = {
                        isSelectionMode = true
                        selectedFileIds = emptySet()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Select", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                TextButton(
                    onClick = {
                        if (selectedFileIds.size == filteredFiles.size) {
                            selectedFileIds = emptySet()
                        } else {
                            selectedFileIds = filteredFiles.map { it.id }.toSet()
                        }
                    }
                ) {
                    Text(
                        if (selectedFileIds.size == filteredFiles.size) "Deselect"
                        else "Select All"
                    )
                }
                IconButton(
                    onClick = {
                        isSelectionMode = false
                        selectedFileIds = emptySet()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search in category
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search in ${category.name.lowercase()}...") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Bulk Actions Bar when items selected
        if (isSelectionMode && selectedFileIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val selectedFiles = filteredFiles.filter { it.id in selectedFileIds }
            val totalSelectedBytes = selectedFiles.sumOf { it.sizeBytes }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${selectedFileIds.size} files (${DriveNode.formatBytes(totalSelectedBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                selectedFiles.forEach { onUploadToCloud(it) }
                                isSelectionMode = false
                                selectedFileIds = emptySet()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload", style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = { showBulkDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Delete (${selectedFileIds.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // File List
        if (filteredFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No matching files" else "No files in this category",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredFiles, key = { it.id }) { file ->
                    val isSelected = file.id in selectedFileIds
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedFileIds = if (isSelected) {
                                            selectedFileIds - file.id
                                        } else {
                                            selectedFileIds + file.id
                                        }
                                    } else {
                                        onFileClick(file, filteredFiles)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedFileIds = setOf(file.id)
                                    }
                                }
                            ),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surface
                        ),
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
                                    contentDescription = if (isSelected) "Selected" else "Not selected",
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            FileThumbnail(file = file, size = 42.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = file.formattedDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = file.formattedSize,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (!isSelectionMode) {
                                PhoneFileOptionsMenu(
                                    file = file,
                                    onOpen = { onFileClick(file, filteredFiles) },
                                    onDetails = { onDetails(file) },
                                    onShare = { onShare(file) },
                                    onUpload = { onUploadToCloud(file) },
                                    onDelete = { fileToDeleteSingle = file }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Single File Delete Confirmation Dialog
        fileToDeleteSingle?.let { file ->
            AlertDialog(
                onDismissRequest = { fileToDeleteSingle = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "Delete File?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to permanently delete \"${file.name}\" (${file.formattedSize}) from your phone storage? This action cannot be undone."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val f = file
                            fileToDeleteSingle = null
                            onDelete(f)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete File", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { fileToDeleteSingle = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Bulk Delete Confirmation Dialog
        if (showBulkDeleteDialog && selectedFileIds.isNotEmpty()) {
            val filesToDelete = filteredFiles.filter { it.id in selectedFileIds }
            val totalSize = filesToDelete.sumOf { it.sizeBytes }
            AlertDialog(
                onDismissRequest = { showBulkDeleteDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "Delete ${filesToDelete.size} Files?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to permanently delete ${filesToDelete.size} files (${DriveNode.formatBytes(totalSize)}) from phone storage? This action cannot be undone."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBulkDeleteDialog = false
                            onDeleteMultiple(filesToDelete)
                            isSelectionMode = false
                            selectedFileIds = emptySet()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete ${filesToDelete.size} Files", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showBulkDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun getPhoneCategoryColor(category: FileCategory): Color {
    return when (category) {
        FileCategory.DOCUMENTS -> Color(0xFF1A73E8)
        FileCategory.VIDEOS -> Color(0xFF8430CE)
        FileCategory.IMAGES -> Color(0xFF137333)
        FileCategory.AUDIO -> Color(0xFFB06000)
        FileCategory.ARCHIVES -> Color(0xFFC5221F)
        FileCategory.ALL, FileCategory.OTHER -> Color(0xFF5F6368)
    }
}
