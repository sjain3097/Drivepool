package com.example.drivepool.data.model

import org.json.JSONArray
import org.json.JSONObject

data class MasterCatalog(
    val version: Int = 1,
    val clusterId: String = "cluster_drivepool_primary",
    val masterNodeId: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val nodes: List<CatalogNodeEntry> = emptyList(),
    val files: List<CatalogFileEntry> = emptyList()
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("version", version)
        root.put("clusterId", clusterId)
        root.put("masterNodeId", masterNodeId)
        root.put("lastUpdated", lastUpdated)

        val nodesArray = JSONArray()
        nodes.forEach { node ->
            val nodeObj = JSONObject()
            nodeObj.put("id", node.id)
            nodeObj.put("email", node.email)
            nodeObj.put("displayName", node.displayName)
            nodeObj.put("role", node.role.name)
            nodeObj.put("totalBytes", node.totalBytes)
            nodeObj.put("usedBytes", node.usedBytes)
            nodesArray.put(nodeObj)
        }
        root.put("nodes", nodesArray)

        val filesArray = JSONArray()
        files.forEach { file ->
            val fileObj = JSONObject()
            fileObj.put("virtualId", file.virtualId)
            fileObj.put("name", file.name)
            fileObj.put("virtualPath", file.virtualPath)
            fileObj.put("sizeBytes", file.sizeBytes)
            fileObj.put("mimeType", file.mimeType)
            fileObj.put("modifiedTime", file.modifiedTime)
            fileObj.put("physicalNodeId", file.physicalNodeId)
            fileObj.put("physicalNodeEmail", file.physicalNodeEmail)
            fileObj.put("remoteDriveFileId", file.remoteDriveFileId)
            fileObj.put("remoteDrivePath", file.remoteDrivePath)
            fileObj.put("checksumSha256", file.checksumSha256)
            fileObj.put("isStarred", file.isStarred)
            filesArray.put(fileObj)
        }
        root.put("files", filesArray)

        return root.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): MasterCatalog {
            val root = JSONObject(jsonStr)
            val version = root.optInt("version", 1)
            val clusterId = root.optString("clusterId", "cluster_drivepool_primary")
            val masterNodeId = root.optString("masterNodeId", "")
            val lastUpdated = root.optLong("lastUpdated", System.currentTimeMillis())

            val nodesList = mutableListOf<CatalogNodeEntry>()
            val nodesArray = root.optJSONArray("nodes")
            if (nodesArray != null) {
                for (i in 0 until nodesArray.length()) {
                    val nObj = nodesArray.getJSONObject(i)
                    nodesList.add(
                        CatalogNodeEntry(
                            id = nObj.getString("id"),
                            email = nObj.getString("email"),
                            displayName = nObj.optString("displayName", nObj.getString("email")),
                            role = NodeRole.valueOf(nObj.optString("role", NodeRole.WORKER.name)),
                            totalBytes = nObj.optLong("totalBytes", 15L * 1024 * 1024 * 1024),
                            usedBytes = nObj.optLong("usedBytes", 0L)
                        )
                    )
                }
            }

            val filesList = mutableListOf<CatalogFileEntry>()
            val filesArray = root.optJSONArray("files")
            if (filesArray != null) {
                for (i in 0 until filesArray.length()) {
                    val fObj = filesArray.getJSONObject(i)
                    filesList.add(
                        CatalogFileEntry(
                            virtualId = fObj.getString("virtualId"),
                            name = fObj.getString("name"),
                            virtualPath = fObj.optString("virtualPath", "/"),
                            sizeBytes = fObj.optLong("sizeBytes", 0L),
                            mimeType = fObj.optString("mimeType", "application/octet-stream"),
                            modifiedTime = fObj.optLong("modifiedTime", System.currentTimeMillis()),
                            physicalNodeId = fObj.getString("physicalNodeId"),
                            physicalNodeEmail = fObj.optString("physicalNodeEmail", ""),
                            remoteDriveFileId = fObj.optString("remoteDriveFileId", ""),
                            remoteDrivePath = fObj.optString("remoteDrivePath", ""),
                            checksumSha256 = fObj.optString("checksumSha256", ""),
                            isStarred = fObj.optBoolean("isStarred", false)
                        )
                    )
                }
            }

            return MasterCatalog(
                version = version,
                clusterId = clusterId,
                masterNodeId = masterNodeId,
                lastUpdated = lastUpdated,
                nodes = nodesList,
                files = filesList
            )
        }
    }
}

data class CatalogNodeEntry(
    val id: String,
    val email: String,
    val displayName: String,
    val role: NodeRole,
    val totalBytes: Long,
    val usedBytes: Long
)

data class CatalogFileEntry(
    val virtualId: String,
    val name: String,
    val virtualPath: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedTime: Long,
    val physicalNodeId: String,
    val physicalNodeEmail: String,
    val remoteDriveFileId: String,
    val remoteDrivePath: String,
    val checksumSha256: String,
    val isStarred: Boolean = false
) {
    fun toPoolFile(): PoolFile {
        return PoolFile(
            id = virtualId,
            name = name,
            virtualPath = virtualPath,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            modifiedTime = modifiedTime,
            physicalNodeId = physicalNodeId,
            physicalNodeEmail = physicalNodeEmail,
            remoteDriveFileId = remoteDriveFileId,
            remoteDrivePath = remoteDrivePath,
            checksumSha256 = checksumSha256,
            isStarred = isStarred
        )
    }
}
