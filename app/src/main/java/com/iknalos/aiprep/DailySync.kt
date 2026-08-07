package com.iknalos.aiprep

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * The 4am job: refresh news, pull any new questions, then update the app itself.
 *
 * Ordering matters. Content is refreshed before the APK check so that if the update
 * installs and restarts the process, the new content is already cached and the app
 * comes back current in one pass rather than two.
 */
class DailySyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Settings(ctx)

        // News: cheap, and the most visibly stale thing if it is skipped.
        try {
            NewsRepository(ctx).fetch()
        } catch (e: Exception) {
            // A dead feed must not stop the content or app update below.
        }

        // Questions and lessons over the air, so new material needs no install.
        var contentVersion = 0
        try {
            val repo = ContentRepository(ctx)
            repo.load(null)
            val synced = ContentSync(ctx).sync(repo.bundledVersion)
            if (synced != null) contentVersion = synced.contentVersion
        } catch (e: Exception) {
            // Keep going; the cached or bundled bank still works.
        }

        // The app itself, only if the user has left auto-update on.
        var updateNote = "skipped"
        if (prefs.autoUpdate) {
            updateNote = try {
                when (val result = Updater(ctx).checkAndInstall()) {
                    is UpdateResult.UpToDate -> "up to date"
                    is UpdateResult.Installing -> "installing ${result.version.versionName}"
                    is UpdateResult.NeedsPermission ->
                        "needs install permission for ${result.version.versionName}"
                    is UpdateResult.NoNetwork -> "no network"
                    is UpdateResult.Failed -> "failed: ${result.reason}"
                }
            } catch (e: Exception) {
                "failed: ${e.message}"
            }
        }

        prefs.recordSync(contentVersion, updateNote)
        // Always success: a failed sync should wait for tomorrow rather than retry in a
        // loop, since nothing here is time-critical.
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "daily-sync-4am"

        /** Local hour the job aims for. Not exact; Doze can shift it by a while. */
        const val TARGET_HOUR = 4

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                // Unmetered by default because the APK is around 15 MB and nobody wants
                // that on a metered connection without asking.
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<DailySyncWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(minutesUntilNextRun(), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                // KEEP would ignore a changed schedule after an app update.
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        /** Runs the same work immediately, for the "Sync now" button. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DailySyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        private fun minutesUntilNextRun(): Long {
            val now = LocalDateTime.now()
            var next = now.with(LocalTime.of(TARGET_HOUR, 0))
            if (!next.isAfter(now)) next = next.plusDays(1)
            return maxOf(1L, Duration.between(now, next).toMinutes())
        }
    }
}

/** Small preference wrapper; not worth a DataStore dependency for four values. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)

    var autoUpdate: Boolean
        get() = prefs.getBoolean("autoUpdate", true)
        set(value) = prefs.edit().putBoolean("autoUpdate", value).apply()

    val lastSyncMillis: Long get() = prefs.getLong("lastSync", 0L)
    val lastSyncNote: String get() = prefs.getString("lastSyncNote", "") ?: ""
    val lastContentVersion: Int get() = prefs.getInt("lastContentVersion", 0)
    val lastInstallStatus: String get() = prefs.getString("lastInstallStatus", "") ?: ""

    fun recordSync(contentVersion: Int, updateNote: String) {
        prefs.edit()
            .putLong("lastSync", System.currentTimeMillis())
            .putString("lastSyncNote", updateNote)
            .apply()
        if (contentVersion > 0) {
            prefs.edit().putInt("lastContentVersion", contentVersion).apply()
        }
    }
}
