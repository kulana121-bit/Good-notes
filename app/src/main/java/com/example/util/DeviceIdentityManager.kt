package com.example.util

import android.content.Context
import java.util.UUID

/**
 * Manages a unique, anonymous, non-PII installation identifier.
 * Stable across app restarts for the lifetime of the installation.
 */
object DeviceIdentityManager {

    private const val PREFS_NAME = "notes_device_identity_prefs"
    private const val KEY_INSTALLATION_ID = "installation_id"

    @Volatile
    private var cachedId: String? = null

    fun getInstallationId(context: Context): String {
        cachedId?.let { return it }

        synchronized(this) {
            cachedId?.let { return it }

            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var id = prefs.getString(KEY_INSTALLATION_ID, null)
            if (id.isNullOrBlank()) {
                id = "dev_" + UUID.randomUUID().toString().replace("-", "").take(16)
                prefs.edit().putString(KEY_INSTALLATION_ID, id).apply()
            }
            cachedId = id
            return id
        }
    }
}
