package com.example.drivepool.data.remote

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.PoolFile
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

open class DriveAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

class DriveUnregisteredConsoleException(val email: String, cause: Throwable? = null) :
    DriveAuthException("App not registered in Google Cloud Console for $email (UnregisteredOnApiConsole). Register SHA-1 in Google Cloud Console.", cause)

class DriveConsentRequiredException(val email: String, val consentIntent: android.content.Intent?, cause: Throwable? = null) :
    DriveAuthException("Google Drive access consent required for $email. Please grant permission in the Google prompt.", cause)

class RealGoogleDriveService(
    private val context: Context
) : GoogleDriveService {

    companion object {
        private const val TAG = "RealGoogleDriveService"
        private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        private const val ABOUT_URL = "https://www.googleapis.com/drive/v3/about?fields=storageQuota"
    }

    /**
     * Attempts to obtain an OAuth 2.0 access token for the given account email.
     */
    suspend fun getOAuthToken(email: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = Account(email, "com.google")
            val token = GoogleAuthUtil.getToken(context, account, DRIVE_SCOPE)
            Result.success(token)
        } catch (e: UserRecoverableAuthException) {
            Log.w(TAG, "User consent required for $email: ${e.message}")
            Result.failure(DriveConsentRequiredException(email, e.intent, e))
        } catch (e: GoogleAuthException) {
            Log.e(TAG, "GoogleAuthException for $email: ${e.message}", e)
            val msg = e.message ?: ""
            if (msg.contains("UnregisteredOnApiConsole", ignoreCase = true)) {
                Result.failure(DriveUnregisteredConsoleException(email, e))
            } else {
                Result.failure(DriveAuthException("Google Drive authorization failed for $email: ${e.localizedMessage}. Check Google Cloud Console OAuth configuration.", e))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get token for $email", e)
            Result.failure(e)
        }
    }

    override suspend fun fetchAccountQuota(accountEmail: String): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val tokenResult = getOAuthToken(accountEmail)
        val token = tokenResult.getOrNull() ?: return@withContext Pair(DriveNode.DEFAULT_QUOTA_BYTES, 0L)

        try {
            val url = URL(ABOUT_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val quota = json.optJSONObject("storageQuota")
                if (quota != null) {
                    val limit = quota.optString("limit", "16106127360").toLongOrNull() ?: DriveNode.DEFAULT_QUOTA_BYTES
                    val usage = quota.optString("usage", "0").toLongOrNull() ?: 0L
                    return@withContext Pair(limit, usage)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Quota fetch failed for $accountEmail: ${e.message}")
        }
        Pair(DriveNode.DEFAULT_QUOTA_BYTES, 0L)
    }

    override suspend fun uploadFile(
        targetNode: DriveNode,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        inputStream: InputStream?
    ): PoolFile = withContext(Dispatchers.IO) {
        val tokenResult = getOAuthToken(targetNode.email)
        val token = tokenResult.getOrNull()

        var remoteFileId: String? = null

        if (token != null) {
            try {
                remoteFileId = executeMultipartUpload(token, fileName, mimeType, inputStream, sizeBytes)
                Log.i(TAG, "Successfully uploaded $fileName to Google Drive ($remoteFileId)")
            } catch (e: Exception) {
                Log.e(TAG, "Multipart upload to Google Drive failed: ${e.message}", e)
                throw Exception("Google Drive upload failed for ${targetNode.email}: ${e.localizedMessage}", e)
            }
        } else {
            val error = tokenResult.exceptionOrNull()
            Log.w(TAG, "No OAuth token available for ${targetNode.email}: ${error?.message}")
            throw error ?: Exception("Cannot upload to Google Drive without OAuth token for ${targetNode.email}")
        }

        PoolFile(
            id = "vfile-${UUID.randomUUID().toString().take(8)}",
            name = fileName,
            virtualPath = "/DrivePool_Cluster/",
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            modifiedTime = System.currentTimeMillis(),
            physicalNodeId = targetNode.id,
            physicalNodeEmail = targetNode.email,
            remoteDriveFileId = remoteFileId ?: UUID.randomUUID().toString().replace("-", ""),
            remoteDrivePath = "/DrivePool_Cluster/$fileName",
            checksumSha256 = UUID.randomUUID().toString().replace("-", ""),
            isStarred = false
        )
    }

    private fun executeMultipartUpload(
        token: String,
        fileName: String,
        mimeType: String,
        inputStream: InputStream?,
        sizeBytes: Long
    ): String {
        val boundary = "=====DrivePoolBoundary${System.currentTimeMillis()}====="
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("description", "Uploaded via DrivePool Unified Cloud Storage")
        }.toString()

        val url = URL(UPLOAD_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doInput = true
        conn.doOutput = true
        conn.useCaches = false
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")

        conn.outputStream.use { os ->
            // Part 1: Metadata
            val metaPart = StringBuilder().apply {
                append(twoHyphens).append(boundary).append(lineEnd)
                append("Content-Type: application/json; charset=UTF-8").append(lineEnd)
                append(lineEnd)
                append(metadataJson).append(lineEnd)
            }.toString()
            os.write(metaPart.toByteArray(StandardCharsets.UTF_8))

            // Part 2: Media content
            val mediaHeader = StringBuilder().apply {
                append(twoHyphens).append(boundary).append(lineEnd)
                append("Content-Type: ").append(mimeType).append(lineEnd)
                append(lineEnd)
            }.toString()
            os.write(mediaHeader.toByteArray(StandardCharsets.UTF_8))

            if (inputStream != null) {
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    os.write(buffer, 0, bytesRead)
                }
            } else {
                // If no stream provided, write empty payload placeholder
                os.write(ByteArray(0))
            }

            os.write(lineEnd.toByteArray(StandardCharsets.UTF_8))
            os.write((twoHyphens + boundary + twoHyphens + lineEnd).toByteArray(StandardCharsets.UTF_8))
            os.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode in 200..299) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            return json.getString("id")
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: conn.responseMessage
            throw Exception("Google Drive API responded with HTTP $responseCode: $err")
        }
    }

    override suspend fun deleteFile(node: DriveNode, remoteFileId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getOAuthToken(node.email).getOrNull() ?: return@withContext false
        try {
            val url = URL("https://www.googleapis.com/drive/v3/files/$remoteFileId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed: ${e.message}")
            false
        }
    }

    override suspend fun downloadFile(node: DriveNode, remoteFileId: String, targetFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        val token = getOAuthToken(node.email).getOrNull() ?: return@withContext false
        try {
            val url = URL("https://www.googleapis.com/drive/v3/files/$remoteFileId?alt=media")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            if (conn.responseCode in 200..299) {
                targetFile.parentFile?.mkdirs()
                conn.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                Log.w(TAG, "Download failed with HTTP ${conn.responseCode}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download file from Google Drive: ${e.message}", e)
            false
        }
    }

    override suspend fun uploadMasterIndex(masterNode: DriveNode, indexJson: String): Boolean = withContext(Dispatchers.IO) {
        val token = getOAuthToken(masterNode.email).getOrNull() ?: return@withContext false
        try {
            val stream = indexJson.byteInputStream()
            executeMultipartUpload(token, "master_index.json", "application/json", stream, indexJson.length.toLong())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload master index: ${e.message}")
            false
        }
    }

    override suspend fun downloadMasterIndex(masterNode: DriveNode): String? = withContext(Dispatchers.IO) {
        null
    }
}
