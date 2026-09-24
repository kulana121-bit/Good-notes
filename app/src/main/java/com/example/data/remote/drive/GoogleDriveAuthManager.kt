package com.example.data.remote.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class GoogleDriveAuthManager(private val context: Context) {

    private val tag = "GoogleDriveAuthManager"

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        val SCOPE_DRIVE_FILE = Scope(DRIVE_FILE_SCOPE)
    }

    private fun buildSignInOptions(preferredEmail: String? = null): GoogleSignInOptions {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(SCOPE_DRIVE_FILE)

        if (!preferredEmail.isNullOrBlank()) {
            builder.setAccountName(preferredEmail.trim())
        }
        return builder.build()
    }

    private fun getClient(preferredEmail: String? = null): GoogleSignInClient {
        return GoogleSignIn.getClient(context, buildSignInOptions(preferredEmail))
    }

    /**
     * Checks whether Google Drive authorization currently exists for a signed-in Google Account.
     */
    fun checkDriveAuthorization(): DriveAuthState {
        return try {
            val account: GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null && GoogleSignIn.hasPermissions(account, SCOPE_DRIVE_FILE)) {
                DriveAuthState.Connected(
                    accountEmail = account.email ?: "Authorized Account",
                    accountName = account.displayName
                )
            } else {
                DriveAuthState.Disconnected
            }
        } catch (t: Throwable) {
            Log.w(tag, "GoogleSignIn check warning: ${t.message}")
            DriveAuthState.Disconnected
        }
    }

    /**
     * Gets the Intent to launch Google Drive OAuth consent flow, associating with preferredEmail if available.
     */
    fun getAuthorizationIntent(preferredEmail: String? = null): Intent {
        return getClient(preferredEmail).signInIntent
    }

    /**
     * Handles the result of the Google Drive authorization Intent.
     */
    fun handleAuthorizationResult(data: Intent?): Result<DriveAuthState.Connected> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            if (account != null && GoogleSignIn.hasPermissions(account, SCOPE_DRIVE_FILE)) {
                Log.i(tag, "Google Drive authorization granted for user account")
                Result.success(
                    DriveAuthState.Connected(
                        accountEmail = account.email ?: "Authorized Account",
                        accountName = account.displayName
                    )
                )
            } else {
                Log.w(tag, "Google Drive permission was not granted by user")
                Result.failure(Exception("Google Drive permission was not granted. Please allow access to store your notes and documents."))
            }
        } catch (e: ApiException) {
            val statusCode = e.statusCode
            Log.w(tag, "Google Drive authorization failed with status: $statusCode")
            val userMessage = when (statusCode) {
                12501 -> "Google Drive authorization was cancelled."
                7 -> "Network error during authorization. Please check internet connection."
                else -> "Authorization failed (${e.localizedMessage ?: "Code $statusCode"})."
            }
            Result.failure(Exception(userMessage))
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error handling authorization result: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /**
     * Obtains an active OAuth access token on background dispatcher.
     * Tokens are NEVER persisted to storage or logged.
     */
    suspend fun getAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: return@withContext Result.failure(Exception("Google Drive is not connected. Please connect Google Drive in Settings."))

        if (!GoogleSignIn.hasPermissions(account, SCOPE_DRIVE_FILE)) {
            return@withContext Result.failure(Exception("Google Drive permission missing. Please reconnect Google Drive."))
        }

        val androidAccount: Account = account.account
            ?: return@withContext Result.failure(Exception("Google account identity could not be retrieved."))

        try {
            val scopeString = "oauth2:$DRIVE_FILE_SCOPE"
            val token = GoogleAuthUtil.getToken(context, androidAccount, scopeString)
            Result.success(token)
        } catch (e: UserRecoverableAuthException) {
            Log.w(tag, "UserRecoverableAuthException encountered during token acquisition")
            Result.failure(e)
        } catch (e: GoogleAuthException) {
            Log.e(tag, "GoogleAuthException during token acquisition: ${e.javaClass.simpleName}")
            Result.failure(Exception("Google Drive authorization error. Please reconnect in Settings."))
        } catch (e: IOException) {
            Log.e(tag, "Network IOException during Drive token acquisition")
            Result.failure(Exception("Network unavailable. Please check your internet connection."))
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error getting Drive token: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /**
     * Revokes or disconnects Google Drive authorization for the current account.
     */
    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = getClient()
            client.revokeAccess()
            client.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(tag, "Error during Drive disconnect: ${e.message}")
            Result.failure(e)
        }
    }
}
