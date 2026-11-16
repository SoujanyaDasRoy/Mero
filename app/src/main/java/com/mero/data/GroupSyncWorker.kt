package com.mero.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mero.MeroApplication
import java.util.concurrent.TimeUnit

/** Every 6 hours, on a network: picks up what friends changed in group playlists while Mero was closed. */
class GroupSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Failures are retried on the next run; a failed run is not worth WorkManager's backoff.
        (applicationContext as MeroApplication).container.groupSync.syncAll()
        return Result.success()
    }

    companion object {
        private const val WORK = "group-sync"

        fun schedule(context: Context) {
            // Guarded like the reminders: this runs in Application.onCreate.
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<GroupSyncWorker>(6, TimeUnit.HOURS)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build(),
                )
            }.onFailure { android.util.Log.w("MeroGroups", "could not schedule group sync", it) }
        }
    }
}
