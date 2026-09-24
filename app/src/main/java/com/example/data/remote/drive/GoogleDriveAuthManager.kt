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
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
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
        // Check Play Services availability
        val playServicesAvailability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        if (playServicesAvailability != ConnectionResult.SUCCESS) {
            Log.w(tag, "Google Play Services is not fully available ($playServicesAvailability) before launching Drive Auth")
        }
        return getClient(preferredEmail).signInIntent
    }

    /**
     * Handles the result of the Google Drive authorization Intent.
     */
    fun handleAuthorizationResult(data: Intent?): Result<DriveAuthState.Connected> {
        if (data == null) {
            return Result.failure(Exception("Google Drive authorization was cancelled (no result returned)."))
        }

        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            if (account != null && GoogleSignIn.hasPermissions(account, SCOPE_DRIVE_FILE)) {
                Log.i(tag, "Google Drive authorization granted for user account: ${account.email}")
                Result.success(
                    DriveAuthState.Connected(
                        accountEmail = account.email ?: "Authorized Account",
                        accountName = account.displayName
                    )
                )
            } else if (account != null) {
                Log.w(tag, "Google Drive scope permission was not granted by user")
                Result.failure(Exception("Google Drive permission was not granted. Please allow Drive file access to store backups and documents."))
            } else {
                Result.failure(Exception("Could not retrieve account details from Google Drive sign-in."))
            }
        } catch (e: ApiException) {
            val statusCode = e.statusCode
            val statusMessage = CommonStatusCodes.getStatusCodeString(statusCode)
            Log.w(tag, "Google Drive authorization failed with status: $statusCode ($statusMessage)")

            val userMessage = when (statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR -> {
                    // Status Code 10
                    "Google Drive OAuth error (Status 10: DEVELOPER_ERROR). The APK signing certificate SHA-1 fingerprint is not registered in Google Cloud Console / Firebase for package ${context.packageName}, or the Google Drive API is not enabled in Google Cloud Console."
                }
                12501, CommonStatusCodes.CANCELED -> {
                    // GoogleSignInStatusCodes.SIGN_IN_CANCELLED (12501)
                    "Google Drive authorization was cancelled by the user."
                }
                12502 -> {
                    // GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS
                    "Google Drive authorization is already in progress. Please wait a moment."
                }
                12500 -> {
                    // GoogleSignInStatusCodes.SIGN_IN_FAILED
                    "Google Sign-In failed (Code 12500). Please ensure Google Play Services is active and up to date."
                }
                CommonStatusCodes.NETWORK_ERROR -> {
                    // Status Code 7
                    "Network error during Google Drive authorization. Please verify internet connection."
                }
                CommonStatusCodes.INVALID_ACCOUNT -> {
                    // Status Code 5
                    "The selected Google Account is invalid. Please try selecting a different account."
                }
                CommonStatusCodes.SIGN_IN_REQUIRED -> {
                    // Status Code 4
                    "Google account sign-in is required before authorizing Drive access."
                }
                CommonStatusCodes.INTERNAL_ERROR -> {
                    // Status Code 8
                    "Google Play Services internal error (Code 8). Please restart the app and try again."
                }
                else -> {
                    val rawMsg = e.localizedMessage?.takeIf { it.isNotBlank() && it != "$statusCode:" }
                    "Google Drive authorization failed (Code $statusCode${if (rawMsg != null) ": $rawMsg" else ""})."
                }
            }
            Result.failure(Exception(userMessage))
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error handling authorization result: ${e.javaClass.simpleName} - ${e.message}")
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
            return@withContext Result.failure(Exception("Google Drive permission missing or revoked. Please reconnect Google Drive in Settings."))
        }

        val androidAccount: Account = account.account
            ?: return@withContext Result.failure(Exception("Google account identity could not be retrieved from signed-in session."))

        try {
            val scopeString = "oauth2:$DRIVE_FILE_SCOPE"
            val token = GoogleAuthUtil.getToken(context, androidAccount, scopeString)
            Result.success(token)
        } catch (e: UserRecoverableAuthException) {
            Log.w(tag, "UserRecoverableAuthException encountered during token acquisition")
            Result.failure(e)
        } catch (e: GoogleAuthException) {
            Log.e(tag, "GoogleAuthException during token acquisition: ${e.javaClass.simpleName} - ${e.message}")
            Result.failure(Exception("Google Drive authorization expired or invalid (${e.message}). Please reconnect in Settings."))
        } catch (e: IOException) {
            Log.e(tag, "Network IOException during Drive token acquisition: ${e.message}")
            Result.failure(Exception("Network unavailable while contacting Google Drive. Please check your internet connection."))
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error getting Drive token: ${e.javaClass.simpleName} - ${e.message}")
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
