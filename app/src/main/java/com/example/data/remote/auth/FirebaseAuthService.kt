package com.example.data.remote.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class UserSummary(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean = false
)

class FirebaseAuthService(private val context: Context) {

    private val tag = "FirebaseAuthService"

    private fun ensureFirebaseInitialized(): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(context)
                } catch (t: Throwable) {
                    Log.w(tag, "Standard Firebase initialization attempt: ${t.message}")
                    val options = try { FirebaseOptions.fromResource(context) } catch (_: Throwable) { null }
                    if (options != null) {
                        try {
                            FirebaseApp.initializeApp(context, options)
                        } catch (optErr: Throwable) {
                            Log.w(tag, "Firebase init with options: ${optErr.message}")
                        }
                    }
                }
            }
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (t: Throwable) {
            Log.w(tag, "Failed to initialize Firebase: ${t.message}")
            false
        }
    }

    private val auth: FirebaseAuth?
        get() = if (ensureFirebaseInitialized()) {
            try {
                FirebaseAuth.getInstance()
            } catch (t: Throwable) {
                Log.w(tag, "FirebaseAuth.getInstance failed: ${t.message}")
                null
            }
        } else null

    private val credentialManager: CredentialManager? by lazy {
        try {
            CredentialManager.create(context)
        } catch (t: Throwable) {
            Log.w(tag, "CredentialManager creation warning: ${t.message}")
            null
        }
    }

    private val _currentUser = MutableStateFlow<UserSummary?>(null)
    val currentUser: StateFlow<UserSummary?> = _currentUser.asStateFlow()

    init {
        ensureFirebaseInitialized()
        try {
            auth?.currentUser?.let { user ->
                _currentUser.value = UserSummary(
                    uid = user.uid,
                    displayName = user.displayName ?: if (user.isAnonymous) "Guest User" else "User",
                    email = user.email ?: if (user.isAnonymous) "guest@synced.cloud" else null,
                    photoUrl = user.photoUrl?.toString(),
                    isAnonymous = user.isAnonymous
                )
            }

            // Continuously listen to Firebase Auth changes to preserve auth state across restarts
            auth?.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                val summary = user?.let {
                    UserSummary(
                        uid = it.uid,
                        displayName = it.displayName ?: if (it.isAnonymous) "Guest User" else "User",
                        email = it.email ?: if (it.isAnonymous) "guest@synced.cloud" else null,
                        photoUrl = it.photoUrl?.toString(),
                        isAnonymous = it.isAnonymous
                    )
                }
                _currentUser.value = summary
            }
        } catch (e: Exception) {
            Log.w(tag, "AuthStateListener initialization: ${e.message}")
        }
    }

    fun authStateFlow(): Flow<UserSummary?> = callbackFlow {
        val authInstance = auth
        if (authInstance == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }

        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            val summary = user?.let {
                UserSummary(
                    uid = it.uid,
                    displayName = it.displayName ?: if (it.isAnonymous) "Guest User" else "User",
                    email = it.email ?: if (it.isAnonymous) "guest@synced.cloud" else null,
                    photoUrl = it.photoUrl?.toString(),
                    isAnonymous = it.isAnonymous
                )
            }
            _currentUser.value = summary
            trySend(summary)
        }
        authInstance.addAuthStateListener(listener)
        awaitClose { authInstance.removeAuthStateListener(listener) }
    }

    private fun resolveActivity(ctx: Context?): Activity? {
        var current = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun resolveWebClientId(webClientId: String?): String {
        if (!webClientId.isNullOrBlank()) {
            return webClientId.trim()
        }

        // 1. Check generated R string in R package "com.example"
        try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", "com.example")
            if (resId != 0) {
                val id = context.getString(resId)
                if (id.isNotBlank()) return id.trim()
            }
        } catch (_: Exception) { }

        // 2. Check context.packageName
        try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                val id = context.getString(resId)
                if (id.isNotBlank()) return id.trim()
            }
        } catch (_: Exception) { }

        // 3. Fallback to canonical web client ID from google-services.json
        return "798861272443-ktn9f4aa93habcpm3464ms28ickvplrd.apps.googleusercontent.com"
    }

    suspend fun signInWithGoogle(activityContext: Context? = null, webClientId: String? = null): Result<UserSummary> = withContext(Dispatchers.IO) {
        val authInstance = auth ?: return@withContext Result.failure(
            Exception("Firebase Authentication could not be connected. Please verify internet access.")
        )

        // Resolve Activity Context for Credential Manager UI display
        val launchContext = resolveActivity(activityContext)
            ?: resolveActivity(context)
            ?: activityContext
            ?: context

        val activeCredentialManager = CredentialManager.create(launchContext)
        val resolvedClientId = resolveWebClientId(webClientId)

        Log.d(tag, "Initiating Google Sign-In with serverClientId resolved")

        val credentialResponse: GetCredentialResponse = try {
            // Step 1: Attempt authorized accounts first with auto-select enabled
            val authorizedOption = GetGoogleIdOption.Builder()
                .setServerClientId(resolvedClientId)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build()

            val authorizedRequest = GetCredentialRequest.Builder()
                .addCredentialOption(authorizedOption)
                .build()

            activeCredentialManager.getCredential(context = launchContext, request = authorizedRequest)
        } catch (e: GetCredentialCancellationException) {
            Log.i(tag, "Google Sign-In cancelled by user")
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            // Step 2: Fallback when no authorized accounts exist - present full Google account chooser
            Log.d(tag, "Authorized account retrieval not matched (${e.javaClass.simpleName}), requesting account selection")
            try {
                val allAccountsOption = GetGoogleIdOption.Builder()
                    .setServerClientId(resolvedClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()

                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(allAccountsOption)
                    .build()

                activeCredentialManager.getCredential(context = launchContext, request = fallbackRequest)
            } catch (cancelEx: GetCredentialCancellationException) {
                Log.i(tag, "Google Sign-In account selection cancelled by user")
                return@withContext Result.failure(cancelEx)
            } catch (fallbackEx: Exception) {
                Log.e(tag, "Google account selection error: ${fallbackEx.javaClass.simpleName} - ${fallbackEx.message}")
                return@withContext Result.failure(fallbackEx)
            }
        }

        // Step 3: Extract Google ID Token from Credential Response
        val credential = credentialResponse.credential
        val idToken: String = try {
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                googleIdTokenCredential.idToken
            } else {
                Log.e(tag, "Unsupported credential type: ${credential.type}")
                return@withContext Result.failure(Exception("Unsupported credential returned from Google Sign-In."))
            }
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(tag, "Failed to parse Google ID token: ${e.message}")
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error parsing credential: ${e.javaClass.simpleName}")
            return@withContext Result.failure(e)
        }

        // Step 4: Authenticate with Firebase using Google Auth Credential
        try {
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = authInstance.signInWithCredential(firebaseCredential).await()
            val user = authResult.user

            if (user != null) {
                val summary = UserSummary(
                    uid = user.uid,
                    displayName = user.displayName ?: "Google User",
                    email = user.email,
                    photoUrl = user.photoUrl?.toString(),
                    isAnonymous = false
                )
                _currentUser.value = summary
                Log.i(tag, "Firebase Google sign-in successful: ${user.uid}")
                Result.success(summary)
            } else {
                Log.e(tag, "Firebase user is null after successful authentication")
                Result.failure(Exception("Firebase user is null after sign in."))
            }
        } catch (e: FirebaseNetworkException) {
            Log.e(tag, "Network error during Firebase sign in: ${e.message}")
            Result.failure(Exception("Network unavailable. Please check your internet connection."))
        } catch (e: FirebaseAuthInvalidUserException) {
            Log.e(tag, "Firebase invalid user: ${e.errorCode}")
            Result.failure(Exception("This Google account is disabled or restricted."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e(tag, "Firebase invalid credentials: ${e.errorCode}")
            Result.failure(Exception("Google Sign-In verification failed (${e.errorCode})."))
        } catch (e: FirebaseException) {
            Log.e(tag, "Firebase sign-in error: ${e.javaClass.simpleName} - ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error during Firebase credential sign-in: ${e.javaClass.simpleName} - ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<UserSummary> = withContext(Dispatchers.IO) {
        val authInstance = auth ?: return@withContext Result.failure(Exception("Firebase Auth not initialized."))
        try {
            val authResult = authInstance.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: return@withContext Result.failure(Exception("User is null after sign in"))
            val summary = UserSummary(
                uid = user.uid,
                displayName = user.displayName ?: email.substringBefore("@"),
                email = user.email ?: email,
                photoUrl = user.photoUrl?.toString(),
                isAnonymous = false
            )
            _currentUser.value = summary
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(tag, "Email Sign-In failed", e)
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String, name: String): Result<UserSummary> = withContext(Dispatchers.IO) {
        val authInstance = auth ?: return@withContext Result.failure(Exception("Firebase Auth not initialized."))
        try {
            val authResult = authInstance.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = authResult.user ?: return@withContext Result.failure(Exception("User is null after registration"))
            if (name.isNotBlank()) {
                try {
                    user.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
                    ).await()
                } catch (_: Exception) { }
            }
            val summary = UserSummary(
                uid = user.uid,
                displayName = name.ifBlank { email.substringBefore("@") },
                email = user.email ?: email,
                photoUrl = user.photoUrl?.toString(),
                isAnonymous = false
            )
            _currentUser.value = summary
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(tag, "Email Registration failed", e)
            Result.failure(e)
        }
    }

    suspend fun signInAnonymously(): Result<UserSummary> = withContext(Dispatchers.IO) {
        val authInstance = auth ?: return@withContext Result.failure(Exception("Firebase Auth not initialized."))
        try {
            val authResult = authInstance.signInAnonymously().await()
            val user = authResult.user ?: return@withContext Result.failure(Exception("Anonymous user is null"))
            val summary = UserSummary(
                uid = user.uid,
                displayName = "Guest User",
                email = "guest@cloudsync.app",
                photoUrl = null,
                isAnonymous = true
            )
            _currentUser.value = summary
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(tag, "Anonymous Sign-In failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth?.signOut()
            try {
                credentialManager?.clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) { }
            _currentUser.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentUserId(): String? {
        return auth?.currentUser?.uid ?: _currentUser.value?.uid
    }
}
