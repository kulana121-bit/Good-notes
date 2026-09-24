package com.example.data.remote.auth

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseInitializer {

    private const val TAG = "FirebaseInitializer"

    @Volatile
    private var isInitialized = false

    @Volatile
    private var lastInitializationError: Throwable? = null

    /**
     * Initializes FirebaseApp safely and reliably.
     * Tries default initialization first, then resource-based FirebaseOptions,
     * and finally explicit fallback options from google-services configuration.
     */
    @Synchronized
    fun initialize(context: Context): FirebaseApp? {
        val appContext = context.applicationContext ?: context

        // Return already initialized default instance if available
        try {
            if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                isInitialized = true
                return FirebaseApp.getInstance()
            }
        } catch (_: Exception) {}

        // Attempt 1: Standard FirebaseApp.initializeApp(context)
        try {
            val app = FirebaseApp.initializeApp(appContext)
            if (app != null && FirebaseApp.getApps(appContext).isNotEmpty()) {
                Log.i(TAG, "Standard FirebaseApp initialization succeeded: [${app.name}]")
                isInitialized = true
                lastInitializationError = null
                return app
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Standard FirebaseApp.initializeApp attempt encountered: ${t.message}")
            lastInitializationError = t
        }

        // Attempt 2: FirebaseOptions.fromResource(appContext)
        try {
            val resourceOptions = FirebaseOptions.fromResource(appContext)
            if (resourceOptions != null) {
                val app = FirebaseApp.initializeApp(appContext, resourceOptions)
                if (app != null) {
                    Log.i(TAG, "FirebaseApp initialized via FirebaseOptions.fromResource: [${app.name}]")
                    isInitialized = true
                    lastInitializationError = null
                    return app
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "FirebaseOptions.fromResource attempt encountered: ${t.message}")
            lastInitializationError = t
        }

        // Attempt 3: Explicit options assembled from app resource identifiers or configuration
        try {
            val explicitOptions = buildOptionsFromResources(appContext)
            if (explicitOptions != null) {
                val app = FirebaseApp.initializeApp(appContext, explicitOptions)
                if (app != null) {
                    Log.i(TAG, "FirebaseApp initialized via explicit FirebaseOptions: [${app.name}]")
                    isInitialized = true
                    lastInitializationError = null
                    return app
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Explicit FirebaseOptions initialization failed: ${t.message}", t)
            lastInitializationError = t
        }

        val finalCheck = try {
            if (FirebaseApp.getApps(appContext).isNotEmpty()) FirebaseApp.getInstance() else null
        } catch (_: Exception) {
            null
        }

        isInitialized = (finalCheck != null)
        return finalCheck
    }

    fun isReady(context: Context): Boolean {
        if (isInitialized) return true
        val app = initialize(context)
        return app != null
    }

    fun getLastError(): Throwable? = lastInitializationError

    private fun buildOptionsFromResources(context: Context): FirebaseOptions? {
        fun getStringRes(name: String): String? {
            val candidatePackages = listOf(context.packageName, "com.example", "com.aistudio.notes.dktzpq")
            for (pkg in candidatePackages) {
                try {
                    val id = context.resources.getIdentifier(name, "string", pkg)
                    if (id != 0) {
                        val str = context.getString(id)
                        if (str.isNotBlank()) return str.trim()
                    }
                } catch (_: Exception) {}
            }
            return null
        }

        val appId = getStringRes("google_app_id") ?: "1:798861272443:android:34eb069c059c2fd10909de"
        val apiKey = getStringRes("google_api_key") ?: "AIzaSyCKVqmHlYgD8oHil8_xPIvVQVtLreWIIT4"
        val projectId = getStringRes("project_id") ?: "gen-lang-client-0491842307"
        val senderId = getStringRes("gcm_defaultSenderId") ?: "798861272443"
        val storageBucket = getStringRes("google_storage_bucket") ?: "gen-lang-client-0491842307.firebasestorage.app"

        return try {
            FirebaseOptions.Builder()
                .setApplicationId(appId)
                .setApiKey(apiKey)
                .setProjectId(projectId)
                .setGcmSenderId(senderId)
                .setStorageBucket(storageBucket)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error constructing FirebaseOptions: ${e.message}", e)
            null
        }
    }
}
