package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class NotesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                // First attempt default auto-init
                try {
                    FirebaseApp.initializeApp(this)
                } catch (_: Exception) { }

                // If still empty, initialize with configured FirebaseOptions from google-services.json
                if (FirebaseApp.getApps(this).isEmpty()) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:798861272443:android:34eb069c059c2fd10909de")
                        .setProjectId("gen-lang-client-0491842307")
                        .setApiKey("AIzaSyCKVqmHlYgD8oHil8_xPIvVQVtLreWIIT4")
                        .setStorageBucket("gen-lang-client-0491842307.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(this, options)
                }
            }
            Log.d("NotesApplication", "Firebase successfully initialized in Application")
        } catch (e: Exception) {
            Log.e("NotesApplication", "Error initializing FirebaseApp", e)
        }
    }
}
