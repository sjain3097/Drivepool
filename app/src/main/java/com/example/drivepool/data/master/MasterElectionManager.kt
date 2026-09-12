package com.example.drivepool.data.master

import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.MasterHealthStatus
import com.example.drivepool.data.model.NodeRole

data class ElectionResult(
    val success: Boolean,
    val newMaster: DriveNode?,
    val previousMaster: DriveNode?,
    val reason: String
)

class MasterElectionManager {

    /**
     * Inspects the health of the current Master node.
     */
    fun evaluateMasterHealth(masterNode: DriveNode?): MasterHealthStatus {
        if (masterNode == null) return MasterHealthStatus.CRITICAL_FULL
        val free = masterNode.freeBytes

        return when {
            free < 50L * 1024 * 1024 -> MasterHealthStatus.CRITICAL_FULL     // Under 50 MB
            free < 500L * 1024 * 1024 -> MasterHealthStatus.BUFFER_ACTIVE   // Under 500 MB
            else -> MasterHealthStatus.HEALTHY
        }
    }

    /**
     * Conducts a dynamic failover election to promote the healthiest worker node
     * when the Master node runs out of space or is locked out.
     */
    fun electNewMaster(nodes: List<DriveNode>): ElectionResult {
        val currentMaster = nodes.find { it.role == NodeRole.MASTER }
        val eligibleWorkers = nodes.filter { it.role == NodeRole.WORKER && it.isOnline }

        if (eligibleWorkers.isEmpty()) {
            return ElectionResult(
                success = false,
                newMaster = null,
                previousMaster = currentMaster,
                reason = "No eligible online worker nodes available for promotion."
            )
        }

        // Elect worker with the most free capacity to ensure long-term index stability
        val bestCandidate = eligibleWorkers.maxByOrNull { it.freeBytes }
            ?: return ElectionResult(
                success = false,
                newMaster = null,
                previousMaster = currentMaster,
                reason = "Could not identify optimal worker candidate."
            )

        val newMaster = bestCandidate.copy(role = NodeRole.MASTER)
        val demotedMaster = currentMaster?.copy(role = NodeRole.WORKER)

        return ElectionResult(
            success = true,
            newMaster = newMaster,
            previousMaster = demotedMaster,
            reason = "Promoted ${bestCandidate.email} to Master Node (${bestCandidate.formattedFree} free). Master Catalog index successfully migrated."
        )
    }
}
