package com.example.drivepool.ui.screens

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
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.NodeRole

@Composable
fun MasterFailoverDialog(
    nodes: List<DriveNode>,
    onDismiss: () -> Unit,
    onConfirmFailover: () -> Unit
) {
    val currentMaster = nodes.find { it.role == NodeRole.MASTER }
    val candidateWorker = nodes.filter { it.role == NodeRole.WORKER && it.isOnline }
        .maxByOrNull { it.freeBytes }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFFD93025),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Dynamic Master Failover", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column {
                Text(
                    text = "If the Master Google Drive is full or locked, the cluster initiates dynamic election to promote the healthiest worker node.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Current Master:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentMaster?.email ?: "None",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Elected Successor (Most Free Space):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = candidateWorker?.let { "${it.email} (${it.formattedFree} free)" } ?: "No eligible worker",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "What will happen:\n1. Master Catalog index is transferred to the successor.\n2. Successor is designated as the new Master coordinator.\n3. Old Master is demoted to Read-Only worker.\n4. Virtual file operations continue uninterrupted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmFailover,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                enabled = candidateWorker != null
            ) {
                Text("Promote Successor")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SimulateMasterFullDialog(
    masterNode: DriveNode?,
    onDismiss: () -> Unit,
    onSimulateRemaining: (bytes: Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFE37400),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simulate Master Drive Full", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column {
                Text(
                    text = "Demonstrate the resilience architecture when the Master Google Drive reaches capacity:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onSimulateRemaining(300L * 1024 * 1024) }, // 300 MB left
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Fill to 300 MB Free (Triggers 500MB Safety Buffer)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onSimulateRemaining(15L * 1024 * 1024) }, // 15 MB left
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD93025)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Fill to 15 MB Free (Triggers Critical Full Alarm)")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
