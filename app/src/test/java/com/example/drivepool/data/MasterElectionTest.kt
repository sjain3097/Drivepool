package com.example.drivepool.data

import com.example.drivepool.data.master.MasterElectionManager
import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.MasterHealthStatus
import com.example.drivepool.data.model.NodeRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterElectionTest {

    private val electionManager = MasterElectionManager()
    private val gb = 1024L * 1024 * 1024
    private val mb = 1024L * 1024

    @Test
    fun testMasterHealthEvaluation() {
        val healthyMaster = DriveNode("m", "m@gmail.com", "M", role = NodeRole.MASTER, totalBytes = 15 * gb, usedBytes = 10 * gb)
        assertEquals(MasterHealthStatus.HEALTHY, electionManager.evaluateMasterHealth(healthyMaster))

        val bufferMaster = DriveNode("m", "m@gmail.com", "M", role = NodeRole.MASTER, totalBytes = 15 * gb, usedBytes = 15 * gb - 300 * mb)
        assertEquals(MasterHealthStatus.BUFFER_ACTIVE, electionManager.evaluateMasterHealth(bufferMaster))

        val criticalMaster = DriveNode("m", "m@gmail.com", "M", role = NodeRole.MASTER, totalBytes = 15 * gb, usedBytes = 15 * gb - 20 * mb)
        assertEquals(MasterHealthStatus.CRITICAL_FULL, electionManager.evaluateMasterHealth(criticalMaster))
    }

    @Test
    fun testDynamicFailoverElectsWorkerWithMostFreeSpace() {
        val master = DriveNode("master", "master@gmail.com", "Master", role = NodeRole.MASTER, totalBytes = 15 * gb, usedBytes = 15 * gb - 10 * mb)
        val workerA = DriveNode("w_a", "workerA@gmail.com", "Worker A", role = NodeRole.WORKER, totalBytes = 15 * gb, usedBytes = 8 * gb) // 7 GB free
        val workerB = DriveNode("w_b", "workerB@gmail.com", "Worker B", role = NodeRole.WORKER, totalBytes = 15 * gb, usedBytes = 2 * gb) // 13 GB free

        val nodes = listOf(master, workerA, workerB)
        val result = electionManager.electNewMaster(nodes)

        assertTrue(result.success)
        assertEquals("w_b", result.newMaster?.id)
        assertEquals(NodeRole.MASTER, result.newMaster?.role)
        assertEquals(NodeRole.WORKER, result.previousMaster?.role)
    }
}
