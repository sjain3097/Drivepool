package com.example.drivepool.data

import com.example.drivepool.data.balancer.EqualStorageBalancer
import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.NodeRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualStorageBalancerTest {

    private val balancer = EqualStorageBalancer()
    private val gb = 1024L * 1024 * 1024
    private val mb = 1024L * 1024

    @Test
    fun testSelectsNodeWithLowestUsageForEqualSegregation() {
        // Node 1 (Master): 8 GB used
        // Node 2 (Worker): 4 GB used
        // Node 3 (Worker): 6 GB used
        val nodes = listOf(
            DriveNode("master", "alex.master@gmail.com", "Master", role = NodeRole.MASTER, totalBytes = 15 * gb, usedBytes = 8 * gb),
            DriveNode("worker1", "worker1@gmail.com", "Worker 1", role = NodeRole.WORKER, totalBytes = 15 * gb, usedBytes = 4 * gb),
            DriveNode("worker2", "worker2@gmail.com", "Worker 2", role = NodeRole.WORKER, totalBytes = 15 * gb, usedBytes = 6 * gb)
        )

        // When a new file of 500 MB is uploaded
        val decision = balancer.selectOptimalNode(nodes, 500 * mb)

        assertNotNull(decision)
        // Should select worker1 because it has the lowest current usage (4 GB)
        assertEquals("worker1", decision!!.targetNode.id)
        assertEquals("worker1@gmail.com", decision.targetNode.email)
    }

    @Test
    fun testProtectsMasterSafetyBufferWhenNearCapacity() {
        // Master node has only 400 MB free (below the 500 MB buffer threshold)
        val nodes = listOf(
            DriveNode("master", "alex.master@gmail.com", "Master", role = NodeRole.MASTER, totalBytes = 15 * gb, usedBytes = 15 * gb - 400 * mb),
            DriveNode("worker1", "worker1@gmail.com", "Worker 1", role = NodeRole.WORKER, totalBytes = 15 * gb, usedBytes = 10 * gb)
        )

        val decision = balancer.selectOptimalNode(nodes, 50 * mb)

        assertNotNull(decision)
        // Master should be skipped because free space < 500 MB buffer
        assertEquals("worker1", decision!!.targetNode.id)
        assertTrue(decision.masterProtected)
    }

    @Test
    fun testBalanceScoreCalculation() {
        // Perfectly equal nodes
        val equalNodes = listOf(
            DriveNode("n1", "n1@gmail.com", "N1", totalBytes = 15 * gb, usedBytes = 5 * gb),
            DriveNode("n2", "n2@gmail.com", "N2", totalBytes = 15 * gb, usedBytes = 5 * gb),
            DriveNode("n3", "n3@gmail.com", "N3", totalBytes = 15 * gb, usedBytes = 5 * gb)
        )

        val score = balancer.calculateBalanceScore(equalNodes)
        assertEquals(100, score)
    }
}
