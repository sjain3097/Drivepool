package com.example.drivepool.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.NodeRole
import com.example.drivepool.data.model.PoolFile

class DrivePoolDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "drivepool_cache.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_NODES = "drive_nodes"
        private const val TABLE_FILES = "pool_files"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NODES (
                id TEXT PRIMARY KEY,
                email TEXT NOT NULL,
                display_name TEXT NOT NULL,
                photo_url TEXT,
                role TEXT NOT NULL,
                total_bytes INTEGER NOT NULL,
                used_bytes INTEGER NOT NULL,
                is_online INTEGER NOT NULL DEFAULT 1,
                is_buffer_protected INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_FILES (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                virtual_path TEXT NOT NULL,
                size_bytes INTEGER NOT NULL,
                mime_type TEXT NOT NULL,
                modified_time INTEGER NOT NULL,
                physical_node_id TEXT NOT NULL,
                physical_node_email TEXT NOT NULL,
                remote_drive_file_id TEXT NOT NULL,
                remote_drive_path TEXT NOT NULL,
                checksum_sha256 TEXT NOT NULL,
                is_starred INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_files_name ON $TABLE_FILES (name)")
        db.execSQL("CREATE INDEX idx_files_node ON $TABLE_FILES (physical_node_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NODES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FILES")
        onCreate(db)
    }

    fun getAllNodes(): List<DriveNode> {
        val nodes = mutableListOf<DriveNode>()
        val db = readableDatabase
        val cursor = db.query(TABLE_NODES, null, null, null, null, null, "role ASC, used_bytes ASC")
        cursor.use {
            while (it.moveToNext()) {
                nodes.add(
                    DriveNode(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        email = it.getString(it.getColumnIndexOrThrow("email")),
                        displayName = it.getString(it.getColumnIndexOrThrow("display_name")),
                        photoUrl = it.getString(it.getColumnIndexOrThrow("photo_url")),
                        role = NodeRole.valueOf(it.getString(it.getColumnIndexOrThrow("role"))),
                        totalBytes = it.getLong(it.getColumnIndexOrThrow("total_bytes")),
                        usedBytes = it.getLong(it.getColumnIndexOrThrow("used_bytes")),
                        isOnline = it.getInt(it.getColumnIndexOrThrow("is_online")) == 1,
                        isBufferProtected = it.getInt(it.getColumnIndexOrThrow("is_buffer_protected")) == 1
                    )
                )
            }
        }
        return nodes
    }

    fun saveNodes(nodes: List<DriveNode>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_NODES, null, null)
            for (node in nodes) {
                val cv = ContentValues().apply {
                    put("id", node.id)
                    put("email", node.email)
                    put("display_name", node.displayName)
                    put("photo_url", node.photoUrl)
                    put("role", node.role.name)
                    put("total_bytes", node.totalBytes)
                    put("used_bytes", node.usedBytes)
                    put("is_online", if (node.isOnline) 1 else 0)
                    put("is_buffer_protected", if (node.isBufferProtected) 1 else 0)
                }
                db.insertWithOnConflict(TABLE_NODES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllFiles(): List<PoolFile> {
        val files = mutableListOf<PoolFile>()
        val db = readableDatabase
        val cursor = db.query(TABLE_FILES, null, null, null, null, null, "modified_time DESC")
        cursor.use {
            while (it.moveToNext()) {
                files.add(
                    PoolFile(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        virtualPath = it.getString(it.getColumnIndexOrThrow("virtual_path")),
                        sizeBytes = it.getLong(it.getColumnIndexOrThrow("size_bytes")),
                        mimeType = it.getString(it.getColumnIndexOrThrow("mime_type")),
                        modifiedTime = it.getLong(it.getColumnIndexOrThrow("modified_time")),
                        physicalNodeId = it.getString(it.getColumnIndexOrThrow("physical_node_id")),
                        physicalNodeEmail = it.getString(it.getColumnIndexOrThrow("physical_node_email")),
                        remoteDriveFileId = it.getString(it.getColumnIndexOrThrow("remote_drive_file_id")),
                        remoteDrivePath = it.getString(it.getColumnIndexOrThrow("remote_drive_path")),
                        checksumSha256 = it.getString(it.getColumnIndexOrThrow("checksum_sha256")),
                        isStarred = it.getInt(it.getColumnIndexOrThrow("is_starred")) == 1
                    )
                )
            }
        }
        return files
    }

    fun saveFiles(files: List<PoolFile>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_FILES, null, null)
            for (file in files) {
                val cv = ContentValues().apply {
                    put("id", file.id)
                    put("name", file.name)
                    put("virtual_path", file.virtualPath)
                    put("size_bytes", file.sizeBytes)
                    put("mime_type", file.mimeType)
                    put("modified_time", file.modifiedTime)
                    put("physical_node_id", file.physicalNodeId)
                    put("physical_node_email", file.physicalNodeEmail)
                    put("remote_drive_file_id", file.remoteDriveFileId)
                    put("remote_drive_path", file.remoteDrivePath)
                    put("checksum_sha256", file.checksumSha256)
                    put("is_starred", if (file.isStarred) 1 else 0)
                }
                db.insertWithOnConflict(TABLE_FILES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_NODES, null, null)
            db.delete(TABLE_FILES, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
