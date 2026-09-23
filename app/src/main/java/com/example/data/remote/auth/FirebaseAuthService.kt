package com.example.data.remote.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
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
                } catch (_: Exception) { }

                if (FirebaseApp.getApps(context).isEmpty()) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:798861272443:android:34eb069c059c2fd10909de")
                        .setProjectId("gen-lang-client-0491842307")
                        .setApiKey("AIzaSyCKVqmHlYgD8oHil8_xPIvVQVtLreWIIT4")
                        .setStorageBucket("gen-lang-client-0491842307.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(context, options)
                }
            }
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize Firebase", e)
            false
        }
    }

    private val auth: FirebaseAuth?
        get() = if (ensureFirebaseInitialized()) FirebaseAuth.getInstance() else null

    private val credentialManager: CredentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<UserSummary?>(null)
    val currentUser: StateFlow<UserSummary?> = _currentUser.asStateFlow()

    init {
        ensureFirebaseInitialized()
        auth?.currentUser?.let { user ->
            _currentUser.value = UserSummary(
                uid = user.uid,
                displayName = user.displayName ?: if (user.isAnonymous) "Guest User" else "User",
                email = user.email ?: if (user.isAnonymous) "guest@synced.cloud" else null,
                photoUrl = user.photoUrl?.toString(),
                isAnonymous = user.isAnonymous
            )
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

    suspend fun signInWithGoogle(webClientId: String? = null): Result<UserSummary> = withContext(Dispatchers.IO) {
        val authInstance = auth ?: return@withContext Result.failure(
            Exception("Firebase Authentication could not be connected. Please ensure internet access.")
        )

        try {
            val resolvedClientId = if (!webClientId.isNullOrBlank()) {
                webClientId
            } else {
                try {
                    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                    if (resId != 0) context.getString(resId) else "798861272443-ktn9f4aa93habcpm3464ms28ickvplrd.apps.googleusercontent.com"
                } catch (_: Exception) {
                    "798861272443-ktn9f4aa93habcpm3464ms28ickvplrd.apps.googleusercontent.com"
                }
            }

            val googleIdOption = GetSignInWithGoogleOption.Builder(
                serverClientId = resolvedClientId
            ).build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context = context, request = request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = authInstance.signInWithCredential(firebaseCredential).await()
                val user = authResult.user

                if (user != null) {
                    val summary = UserSummary(
                        uid = user.uid,
                        displayName = user.displayName ?: googleIdTokenCredential.displayName,
                        email = user.email ?: googleIdTokenCredential.id,
                        photoUrl = user.photoUrl?.toString() ?: googleIdTokenCredential.profilePictureUri?.toString(),
                        isAnonymous = false
                    )
                    _currentUser.value = summary
                    return@withContext Result.success(summary)
                }
            }
            Result.failure(Exception("Google Sign-In returned unsupported credential type."))
        } catch (e: GetCredentialCancellationException) {
            Log.w(tag, "Google Sign-In was cancelled by user: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Google Sign-In failed", e)
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
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
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
