package com.example.drivepool.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.PoolFile

@Composable
fun RebalanceDialog(
    file: PoolFile,
    availableNodes: List<DriveNode>,
    onDismiss: () -> Unit,
    onConfirm: (targetNodeId: String) -> Unit
) {
    var selectedNodeId by remember {
        mutableStateOf(availableNodes.firstOrNull()?.id.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Migrate File to Another Account", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column {
                Text(
                    text = "Transfer \"${file.name}\" (${file.formattedSize}) from ${file.physicalNodeEmail} to another connected Google Drive:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (availableNodes.isEmpty()) {
                    Text(
                        text = "No other online Google accounts available in the cluster.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    availableNodes.forEach { node ->
                        val isSelected = node.id == selectedNodeId
                        val hasEnoughSpace = node.freeBytes >= file.sizeBytes

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = hasEnoughSpace) {
                                    selectedNodeId = node.id
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { if (hasEnoughSpace) selectedNodeId = node.id },
                                    enabled = hasEnoughSpace
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = node.email,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${node.formattedFree} free of ${node.formattedTotal}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (hasEnoughSpace) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedNodeId.isNotEmpty()) {
                        onConfirm(selectedNodeId)
                    }
                },
                enabled = selectedNodeId.isNotEmpty()
            ) {
                Text("Transfer & Update Index")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
