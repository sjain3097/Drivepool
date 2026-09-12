package com.example.drivepool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivepool.data.model.MasterHealthStatus
import com.example.drivepool.data.model.NodeRole
import com.example.drivepool.ui.components.NodeBadge
import com.example.drivepool.ui.components.StorageProgressBar
import com.example.drivepool.ui.viewmodel.DrivePoolUiState
import com.example.drivepool.ui.viewmodel.DrivePoolViewModel

@Composable
fun ClusterDashboardScreen(
    state: DrivePoolUiState,
    viewModel: DrivePoolViewModel,
    modifier: Modifier = Modifier
) {
    val stats = state.clusterStats

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Storage Pool & Cluster Health",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Multi-Account Aggregation Architecture",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.nodes.isNotEmpty()) {
                OutlinedButton(
                    onClick = { viewModel.resetCluster() },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear Pool")
                }
            }
        }

        // POOLED VIRTUAL DRIVE CAPACITY CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Aggregated Cloud Space",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${stats.nodeCount} Connected Accounts Pooled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                StorageProgressBar(
                    usedPercent = stats.overallUsagePercent,
                    formattedUsed = stats.formattedUsed,
                    formattedTotal = stats.formattedTotal,
                    barHeight = 12.dp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricMiniBox(label = "Total Pooled", value = stats.formattedTotal)
                    MetricMiniBox(label = "Total Used", value = stats.formattedUsed)
                    MetricMiniBox(label = "Total Free Space", value = stats.formattedFree, color = Color(0xFF137333))
                }
            }
        }

        // BACKEND EQUAL SEGREGATION BALANCE CARD
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
                        imageVector = Icons.Default.Balance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Equal Backend Segregation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${stats.balanceScorePercent}% Balanced",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (stats.balanceScorePercent > 80) Color(0xFF137333) else Color(0xFFE37400)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Incoming files are automatically placed on the account with the lowest used storage to maintain equal distribution across all connected Google accounts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // MASTER NODE & FAST INDEX CARD (Handling Master Drive Full scenario)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (stats.masterHealth) {
                    MasterHealthStatus.HEALTHY -> MaterialTheme.colorScheme.surface
                    MasterHealthStatus.BUFFER_ACTIVE -> Color(0xFFFFF8E1)
                    MasterHealthStatus.CRITICAL_FULL -> Color(0xFFFCE8E6)
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = when (stats.masterHealth) {
                            MasterHealthStatus.HEALTHY -> Color(0xFF1A73E8)
                            MasterHealthStatus.BUFFER_ACTIVE -> Color(0xFFE37400)
                            MasterHealthStatus.CRITICAL_FULL -> Color(0xFFD93025)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Master Node & Catalog Index",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stats.masterNodeEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val (statusText, statusBg, statusColor) = when (stats.masterHealth) {
                        MasterHealthStatus.HEALTHY -> Triple("Healthy", Color(0xFFE6F4EA), Color(0xFF137333))
                        MasterHealthStatus.BUFFER_ACTIVE -> Triple("500MB Buffer Active", Color(0xFFFEF7E0), Color(0xFFB06000))
                        MasterHealthStatus.CRITICAL_FULL -> Triple("Critically Full", Color(0xFFFCE8E6), Color(0xFFC5221F))
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = statusText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricMiniBox(label = "Index Entries", value = "${stats.catalogEntriesCount} Files")
                    MetricMiniBox(label = "Index Size", value = stats.formattedCatalogSize)
                    MetricMiniBox(label = "Search Latency", value = "< 1 ms (FTS)", color = Color(0xFF137333))
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = when (stats.masterHealth) {
                        MasterHealthStatus.HEALTHY ->
                            "500 MB reserve buffer is active. Master drive hosts the master_index.json catalog for instant search across all worker accounts."
                        MasterHealthStatus.BUFFER_ACTIVE ->
                            "MASTER HEADROOM PROTECTION ENGAGED: Master has < 500 MB free. File uploads are diverted 100% to Worker accounts to safeguard catalog index writes."
                        MasterHealthStatus.CRITICAL_FULL ->
                            "CRITICAL STORAGE WARNING: Master drive is nearly full (< 50 MB). Failover promotion is recommended to migrate the Master Catalog to a healthy worker node."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (stats.masterHealth) {
                        MasterHealthStatus.HEALTHY -> MaterialTheme.colorScheme.onSurfaceVariant
                        MasterHealthStatus.BUFFER_ACTIVE -> Color(0xFFB06000)
                        MasterHealthStatus.CRITICAL_FULL -> Color(0xFFC5221F)
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Failover Promotion Button (when multiple nodes exist)
                if (state.nodes.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.setFailoverDialogVisible(true) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (stats.masterHealth != MasterHealthStatus.HEALTHY) Color(0xFFD93025) else Color(0xFF1A73E8)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Dynamic Master Failover", fontSize = 12.sp)
                    }
                }
            }
        }

        // PER-ACCOUNT NODE STORAGE BREAKDOWN
        Text(
            text = "Connected Google Accounts (${state.nodes.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (state.nodes.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No Google Accounts Connected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Go to the Accounts tab to sign in or add your Google accounts. Each account pools 15 GB of free Drive storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            state.nodes.forEach { node ->
                NodeStorageCard(node = node)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Dialogs
    if (state.showFailoverDialog) {
        MasterFailoverDialog(
            nodes = state.nodes,
            onDismiss = { viewModel.setFailoverDialogVisible(false) },
            onConfirmFailover = { viewModel.triggerMasterFailover() }
        )
    }
}

@Composable
private fun NodeStorageCard(node: com.example.drivepool.data.model.DriveNode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = node.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                NodeBadge(role = node.role)
            }

            Spacer(modifier = Modifier.height(10.dp))

            StorageProgressBar(
                usedPercent = node.usagePercent,
                formattedUsed = node.formattedUsed,
                formattedTotal = node.formattedTotal,
                isBufferProtected = node.isBufferProtected
            )
        }
    }
}

@Composable
private fun MetricMiniBox(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
