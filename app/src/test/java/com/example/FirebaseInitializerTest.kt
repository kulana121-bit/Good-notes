package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.auth.FirebaseInitializer
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseInitializerTest {

    @Test
    fun testFirebaseInitializerInitializesApp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = FirebaseInitializer.initialize(context)
        assertNotNull("FirebaseApp should be initialized", app)
        assertTrue("FirebaseApp apps list should not be empty", FirebaseApp.getApps(context).isNotEmpty())
        assertTrue("FirebaseInitializer isReady should be true", FirebaseInitializer.isReady(context))
    }
}
