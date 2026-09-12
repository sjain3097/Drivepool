package com.example.drivepool.data.remote

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.drivepool.data.model.DriveNode
import com.example.drivepool.data.model.NodeRole
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import java.util.UUID

data class DiscoveredDeviceAccount(
    val email: String,
    val name: String,
    val isAlreadyConnected: Boolean = false
)

class GoogleAuthManager(
    private val context: Context
) {
    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_METADATA_SCOPE = "https://www.googleapis.com/auth/drive.metadata.readonly"
    }

    /**
     * Builds the GoogleSignInClient configured with Google Drive scopes.
     */
    fun getGoogleSignInClient(activity: Activity): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(
                Scope(DRIVE_FILE_SCOPE),
                Scope(DRIVE_METADATA_SCOPE)
            )
            .build()

        return GoogleSignIn.getClient(activity, gso)
    }

    /**
     * Inspects Google accounts already logged in on the Android device.
     */
    fun getDeviceLoggedGoogleAccounts(connectedEmails: Set<String>): List<DiscoveredDeviceAccount> {
        val discovered = mutableListOf<DiscoveredDeviceAccount>()

        try {
            val accountManager = AccountManager.get(context)
            val accounts: Array<Account> = accountManager.getAccountsByType("com.google")

            for (account in accounts) {
                val email = account.name
                val displayName = email.substringBefore("@").replace(".", " ").capitalizeWords()
                val isConnected = connectedEmails.any { it.equals(email, ignoreCase = true) }

                discovered.add(
                    DiscoveredDeviceAccount(
                        email = email,
                        name = displayName,
                        isAlreadyConnected = isConnected
                    )
                )
            }
        } catch (_: SecurityException) {
            // Permission not yet granted
        } catch (_: Exception) {
            // AccountManager exception fallback
        }

        return discovered
    }

    /**
     * Parses the result intent returned by GoogleSignInClient.signInIntent.
     */
    fun parseSignInResult(data: Intent?): Result<DriveNode> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)

            val email = account.email ?: return Result.failure(Exception("Google account has no email"))
            val name = account.displayName ?: email.substringBefore("@")
            val photoUrl = account.photoUrl?.toString()

            val node = DriveNode(
                id = "google_${account.id ?: UUID.randomUUID().toString().take(8)}",
                email = email,
                displayName = name,
                photoUrl = photoUrl,
                role = NodeRole.WORKER,
                totalBytes = DriveNode.DEFAULT_QUOTA_BYTES,
                usedBytes = 0L,
                isOnline = true,
                isBufferProtected = false
            )

            Result.success(node)
        } catch (e: ApiException) {
            Result.failure(Exception("Google Sign-In failed (status code: ${e.statusCode}): ${e.localizedMessage}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates an Intent to launch the system Google account chooser.
     */
    fun createSystemAccountPickerIntent(): Intent {
        return AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            null,
            null,
            null,
            null
        )
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}
