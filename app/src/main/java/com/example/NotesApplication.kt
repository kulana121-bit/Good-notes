package com.example

import android.app.Application
import android.util.Log
import com.example.data.remote.auth.FirebaseInitializer

class NotesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            val app = FirebaseInitializer.initialize(this)
            if (app != null) {
                Log.i("NotesApplication", "Firebase successfully initialized in NotesApplication: ${app.name}")
            } else {
                Log.w("NotesApplication", "Firebase initialization returned null. Details: ${FirebaseInitializer.getLastError()?.message}")
            }
        } catch (t: Throwable) {
            Log.e("NotesApplication", "Unhandled error initializing Firebase in NotesApplication", t)
        }
    }
}
