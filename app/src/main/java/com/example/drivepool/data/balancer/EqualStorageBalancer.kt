package com.example.drivepool.data.balancer

import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.NodeRole
import kotlin.math.abs

data class AllocationDecision(
    val targetNode: DriveNode,
    val explanation: String,
    val previousUsage: Long,
    val projectedUsage: Long,
    val masterProtected: Boolean = false
)

class EqualStorageBalancer {

    companion object {
        // 500 MB reserved exclusively for the Master Index revisions, locks, and sync logs
        const val MASTER_SAFETY_BUFFER_BYTES: Long = 500L * 1024 * 1024
        // Minimum free space threshold before marking a node as full
        const val MINIMUM_HEADROOM_BYTES: Long = 10L * 1024 * 1024 // 10 MB
    }

    /**
     * Determines the optimal node for storing a new file to maintain equal segregation
     * across all connected Google accounts, while strictly safeguarding the Master Index buffer.
     */
    fun selectOptimalNode(nodes: List<DriveNode>, fileSizeBytes: Long): AllocationDecision? {
        if (nodes.isEmpty()) return null

        val masterNode = nodes.find { it.role == NodeRole.MASTER }
        val isMasterProtected = masterNode != null && (masterNode.freeBytes - fileSizeBytes) < MASTER_SAFETY_BUFFER_BYTES

        // Filter eligible nodes
        val eligibleNodes = nodes.filter { node ->
            if (!node.isOnline) return@filter false

            // Check if node has sufficient space for file + minimum headroom
            if (node.freeBytes < (fileSizeBytes + MINIMUM_HEADROOM_BYTES)) {
                return@filter false
            }

            // Protect master if this upload would compromise its safety reserve buffer
            if (node.role == NodeRole.MASTER && isMasterProtected) {
                return@filter false
            }

            true
        }

        // If no node is eligible (e.g. all workers full and master protected), check if workers can take it
        val candidates = if (eligibleNodes.isNotEmpty()) {
            eligibleNodes
        } else {
            // Emergency fallback: Any node with enough raw free space
            nodes.filter { it.isOnline && it.freeBytes >= fileSizeBytes }
        }

        if (candidates.isEmpty()) return null

        // Equal Segregation Strategy:
        // Prioritize the node with the lowest used storage to equalize usage across accounts.
        val selectedNode = candidates.minWithOrNull(
            compareBy<DriveNode> { it.usedBytes }
                .thenByDescending { it.freeBytes }
        ) ?: candidates.first()

        val explanation = when {
            selectedNode.role == NodeRole.MASTER && isMasterProtected ->
                "Routing to Master as emergency fallback (Buffer threshold active)."
            selectedNode.role != NodeRole.MASTER && isMasterProtected ->
                "Master node buffer protected (< 500 MB free). Routed to Worker (${selectedNode.email}) to maintain equal balance."
            else ->
                "Selected ${selectedNode.email} to equalize storage (Currently lowest utilization at ${selectedNode.formattedUsed})."
        }

        return AllocationDecision(
            targetNode = selectedNode,
            explanation = explanation,
            previousUsage = selectedNode.usedBytes,
            projectedUsage = selectedNode.usedBytes + fileSizeBytes,
            masterProtected = isMasterProtected
        )
    }

    /**
     * Computes the balance score (0% to 100%) of the cluster.
     * 100% means perfectly equal distribution across all nodes.
     */
    fun calculateBalanceScore(nodes: List<DriveNode>): Int {
        if (nodes.size <= 1) return 100

        val usages = nodes.map { it.usedBytes.toDouble() }
        val total = usages.sum()
        if (total == 0.0) return 100 // all empty = perfectly balanced

        val mean = total / nodes.size
        // Calculate average absolute deviation from mean
        val meanDeviation = usages.map { abs(it - mean) }.sum() / nodes.size
        val maxPossibleDeviation = mean * 2.0

        val balance = (1.0 - (meanDeviation / maxPossibleDeviation)).coerceIn(0.0, 1.0)
        return (balance * 100).toInt()
    }
}
