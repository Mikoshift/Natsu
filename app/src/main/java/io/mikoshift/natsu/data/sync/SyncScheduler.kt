package io.mikoshift.natsu.data.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SyncScheduler(
    context: Context,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedule(delaySeconds: Long = 0) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .apply {
                if (delaySeconds > 0) {
                    setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                }
            }
            .build()

        workManager.enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        private const val ONE_TIME_WORK_NAME = "natsu_sync_once"
        private const val PERIODIC_WORK_NAME = "natsu_sync_periodic"
    }
}
