package com.example.data.remote.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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
    val photoUrl: String?
)

class FirebaseAuthService(private val context: Context) {

    private val tag = "FirebaseAuthService"

    private val isFirebaseInitialized: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    private val auth: FirebaseAuth?
        get() = if (isFirebaseInitialized) FirebaseAuth.getInstance() else null

    private val credentialManager: CredentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<UserSummary?>(null)
    val currentUser: StateFlow<UserSummary?> = _currentUser.asStateFlow()

    init {
        auth?.currentUser?.let { user ->
            _currentUser.value = UserSummary(
                uid = user.uid,
                displayName = user.displayName,
                email = user.email,
                photoUrl = user.photoUrl?.toString()
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
                    displayName = it.displayName,
                    email = it.email,
                    photoUrl = it.photoUrl?.toString()
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
            Exception("Firebase is not configured. Add google-services.json to connect cloud authentication.")
        )

        try {
            // One option per request per Credential Manager best practices
            val googleIdOption = if (webClientId.isNullOrBlank()) {
                GetSignInWithGoogleOption.Builder(
                    serverClientId = "placeholder-client-id.apps.googleusercontent.com"
                ).build()
            } else {
                GetSignInWithGoogleOption.Builder(serverClientId = webClientId).build()
            }

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
                        photoUrl = user.photoUrl?.toString() ?: googleIdTokenCredential.profilePictureUri?.toString()
                    )
                    _currentUser.value = summary
                    return@withContext Result.success(summary)
                }
            }
            Result.failure(Exception("Unsupported credential type returned"))
        } catch (e: GetCredentialCancellationException) {
            Log.w(tag, "Google Sign-In was cancelled by user: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth?.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
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
