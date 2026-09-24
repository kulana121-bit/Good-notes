package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class NotesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeFirebaseSafely()
    }

    private fun initializeFirebaseSafely() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(this)
                } catch (t: Throwable) {
                    Log.w("NotesApplication", "Default FirebaseApp.initializeApp skipped or failed: ${t.message}")
                    val options = try {
                        FirebaseOptions.fromResource(this)
                    } catch (_: Throwable) {
                        null
                    }
                    if (options != null) {
                        try {
                            FirebaseApp.initializeApp(this, options)
                        } catch (initErr: Throwable) {
                            Log.w("NotesApplication", "FirebaseApp init with options failed: ${initErr.message}")
                        }
                    }
                }
            }
            Log.d("NotesApplication", "Firebase check completed in NotesApplication")
        } catch (t: Throwable) {
            Log.e("NotesApplication", "Safe error initializing FirebaseApp", t)
        }
    }
}

