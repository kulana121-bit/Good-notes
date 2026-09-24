package com.example.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.NotesDatabase
import java.util.concurrent.TimeUnit

class NotesSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = NotesDatabase.getInstance(applicationContext)
        val syncManager = SyncManager(applicationContext, database)

        return try {
            if (syncManager.isOnline()) {
                val syncResult = syncManager.syncNow()
                if (syncResult.isSuccess) {
                    Result.success()
                } else {
                    val errorMsg = syncResult.exceptionOrNull()?.message ?: ""
                    if (errorMsg.contains("Sign in", ignoreCase = true)) {
                        Result.success()
                    } else {
                        Result.retry()
                    }
                }
            } else {
                Result.retry()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "NotesPeriodicSyncWorker"

        fun schedulePeriodicSync(context: Context) {
            NotesCloudSyncWorker.schedulePeriodicSync(context)
        }
    }
}
