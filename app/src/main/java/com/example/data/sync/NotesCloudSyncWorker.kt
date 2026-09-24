package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.backup.CloudBackupManager
import com.example.data.local.NotesDatabase
import com.example.data.remote.auth.FirebaseAuthService
import com.example.data.remote.drive.DriveAuthState
import com.example.data.remote.drive.GoogleDriveService
import java.util.concurrent.TimeUnit

class NotesCloudSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val tag = "NotesCloudSyncWorker"

    override suspend fun doWork(): Result {
        val database = NotesDatabase.getInstance(applicationContext)
        val authService = FirebaseAuthService(applicationContext)
        val driveService = GoogleDriveService(applicationContext)
        val syncManager = SyncManager(applicationContext, database)
        val cloudBackupManager = CloudBackupManager(applicationContext, database)

        val uid = authService.getCurrentUserId()
        if (uid.isNullOrBlank()) {
            Log.d(tag, "No authenticated user. Skipping cloud sync worker cycle.")
            return Result.success()
        }

        if (!syncManager.isOnline()) {
            Log.d(tag, "Device is offline. Will retry when connectivity is restored.")
            return Result.retry()
        }

        return try {
            // 1. SYNC FIRESTORE STRUCTURED DATA
            Log.d(tag, "Executing Firestore sync...")
            val firestoreSyncResult = syncManager.syncNow()
            if (firestoreSyncResult.isFailure) {
                val err = firestoreSyncResult.exceptionOrNull()?.message ?: ""
                Log.w(tag, "Firestore sync warning: $err")
                // Transient network or server error: retry with exponential backoff
                if (err.contains("network", ignoreCase = true) || err.contains("timeout", ignoreCase = true)) {
                    return Result.retry()
                }
            }

            // 2. CHECK AUTOMATIC CLOUD BACKUP PREFERENCES
            val autoBackupPref = database.settingDao().getSettingDirect("auto_cloud_backup") ?: "true"
            val wifiOnlyPref = database.settingDao().getSettingDirect("cloud_backup_wifi_only") ?: "false"
            val includeDocsPref = database.settingDao().getSettingDirect("cloud_backup_include_docs") ?: "true"

            if (autoBackupPref.toBoolean()) {
                val isDriveConnected = driveService.checkAuthorization() is DriveAuthState.Connected
                if (isDriveConnected) {
                    val isWifi = isWifiConnected(applicationContext)
                    if (wifiOnlyPref.toBoolean() && !isWifi) {
                        Log.d(tag, "Drive backup skipped: Wi-Fi only constraint active and currently on cellular.")
                    } else {
                        Log.d(tag, "Executing Google Drive cloud backup...")
                        val backupResult = cloudBackupManager.backupToCloud(includeDocuments = includeDocsPref.toBoolean())
                        if (backupResult.isFailure) {
                            Log.w(tag, "Drive cloud backup warning: ${backupResult.exceptionOrNull()?.message}")
                        }
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Sync cycle encountered unexpected error", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    companion object {
        const val WORK_NAME = "NotesUnifiedCloudSyncWorker"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<NotesCloudSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }

        fun scheduleImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<NotesCloudSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "NotesImmediateSync",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancelSync(context: Context) {
            try {
                val wm = WorkManager.getInstance(context)
                wm.cancelUniqueWork(WORK_NAME)
                wm.cancelUniqueWork("NotesImmediateSync")
                Log.d(WORK_NAME, "All cloud sync background jobs cancelled.")
            } catch (e: Exception) {
                Log.w(WORK_NAME, "Failed to cancel sync work", e)
            }
        }
    }
}
