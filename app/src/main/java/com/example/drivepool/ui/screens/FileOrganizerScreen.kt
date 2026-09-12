package com.example.drivepool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivepool.data.model.CategoryIndexSummary
import com.example.drivepool.data.model.FileCategory
import com.example.drivepool.data.model.PoolFile
import com.example.drivepool.ui.components.FileIcon
import com.example.drivepool.ui.components.FileThumbnail
import com.example.drivepool.ui.components.StorageProgressBar
import com.example.drivepool.ui.viewmodel.DrivePoolUiState
import com.example.drivepool.ui.viewmodel.DrivePoolViewModel

@Composable
fun FileOrganizerScreen(
    state: DrivePoolUiState,
    viewModel: DrivePoolViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedCategory = state.selectedCategoryFolder

    if (selectedCategory != null) {
        // Detailed Category Folder View
        CategoryFolderDetailView(
            category = selectedCategory,
            files = state.files.filter { it.category == selectedCategory },
            onBack = { viewModel.onCategoryFolderSelected(null) },
            onFileClick = { viewModel.onFileSelected(it) },
            modifier = modifier
        )
    } else {
        // Main Organizer Hub
        OrganizerMainView(
            state = state,
            viewModel = viewModel,
            modifier = modifier
        )
    }

    state.selectedFileForDetails?.let { file ->
        FileDetailsSheet(
            file = file,
            nodes = state.nodes,
            onDismiss = { viewModel.onFileSelected(null) },
            onDelete = { viewModel.deleteFile(it) },
            onRebalance = { f, targetId -> viewModel.rebalanceFile(f, targetId) },
            onView = { viewModel.previewPoolFile(it, state.files) },
            onShare = { viewModel.sharePoolFile(context, it) }
        )
    }
}

@Composable
private fun OrganizerMainView(
    state: DrivePoolUiState,
    viewModel: DrivePoolViewModel,
    modifier: Modifier = Modifier
) {
    val insights = state.organizerInsights

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
                    text = "Smart File Organizer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Indexed categorization across all pooled Google accounts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(
                onClick = { viewModel.runAutoOrganize() },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Auto-Organize", fontSize = 12.sp)
            }
        }

        // Category Breakdown Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Indexed Category Storage",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${insights.totalIndexedFiles} Files • ${insights.formattedTotalIndexed}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category mini breakdown rows
                insights.categorySummaries.forEach { summary ->
                    CategoryProgressRow(summary = summary)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        // INDEXED CATEGORIES GRID / LIST
        Text(
            text = "Categories & Virtual Folders",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        insights.categorySummaries.forEach { summary ->
            CategoryCard(
                summary = summary,
                onClick = { viewModel.onCategoryFolderSelected(summary.category) }
            )
        }

        // TOP LARGEST FILES SECTION
        if (insights.largestFiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Largest Files Consuming Space",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            insights.largestFiles.forEach { file ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onFileSelected(file) },
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
                                text = "Located on ${file.physicalNodeEmail}",
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
                    }
                }
            }
        }

        // DUPLICATE DETECTION / CLEANUP ASSISTANT
        if (insights.duplicateCandidates.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
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
                            text = "Potential Duplicates Found",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB06000)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    insights.duplicateCandidates.forEach { dup ->
                        Text(
                            text = "• \"${dup.fileName}\" stored across ${dup.files.size} accounts (Potential savings: ${dup.formattedSavings})",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5D3E00)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CategoryProgressRow(summary: CategoryIndexSummary) {
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
                    .background(getCategoryColor(summary.category))
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
private fun CategoryCard(
    summary: CategoryIndexSummary,
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
            FileIcon(category = summary.category, size = 46.dp)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.category.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${summary.fileCount} Files • ${summary.formattedTotalSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open Folder",
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun CategoryFolderDetailView(
    category: FileCategory,
    files: List<PoolFile>,
    onBack: () -> Unit,
    onFileClick: (PoolFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSize = files.sumOf { it.sizeBytes }

    Column(modifier = modifier.fillMaxSize()) {
        // Back Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(4.dp))
            FileIcon(category = category, size = 36.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = category.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${files.size} Indexed Files • ${com.example.drivepool.data.model.DriveNode.formatBytes(totalSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No ${category.name.lowercase()} found in the cluster.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.id }) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFileClick(file) },
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
                            FileThumbnail(file = file, size = 46.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${file.formattedSize} • Stored on ${file.physicalNodeEmail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getCategoryColor(category: FileCategory): Color {
    return when (category) {
        FileCategory.DOCUMENTS -> Color(0xFF1A73E8)
        FileCategory.VIDEOS -> Color(0xFF8430CE)
        FileCategory.IMAGES -> Color(0xFF137333)
        FileCategory.AUDIO -> Color(0xFFB06000)
        FileCategory.ARCHIVES -> Color(0xFFC5221F)
        FileCategory.ALL, FileCategory.OTHER -> Color(0xFF5F6368)
    }
}
