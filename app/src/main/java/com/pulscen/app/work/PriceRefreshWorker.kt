package com.pulscen.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pulscen.app.PulsCenApplication
import java.util.concurrent.TimeUnit

class PriceRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? PulsCenApplication ?: return Result.success()
        val container = app.container
        if (!container.repository.restoreSession()) return Result.success()
        return try {
            container.priceRefresher.refreshAll()
            container.syncManager.push()
            Result.success()
        } catch (error: Exception) {
            Result.retry()
        }
    }
}

object WorkScheduler {
    private const val PERIODIC_NAME = "price_refresh_periodic"
    private const val ONCE_NAME = "price_refresh_once"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<PriceRefreshWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<PriceRefreshWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONCE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
