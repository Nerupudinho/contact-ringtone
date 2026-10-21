package com.nerupudinho.contactringtone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Gives newly added contacts the ringtone. Never overwrites tones set by hand. */
class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        if (!RingtoneSync.prefs(ctx).getBoolean(RingtoneSync.KEY_AUTO, true)) return Result.success()

        val granted = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
            .all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (granted) runCatching { RingtoneSync.apply(ctx, overwriteManual = false) }

        if (inputData.getBoolean(KEY_REARM, false)) Scheduler.armContentTrigger(ctx, fromWorker = true)
        return Result.success()
    }

    companion object {
        const val KEY_REARM = "rearm"
    }
}

/**
 * Two light-weight triggers, both handled by Android's job scheduler (no service
 * running in the background):
 *  - a job that wakes ~30s after the contacts list changes
 *  - a safety-net run every 12 hours
 */
object Scheduler {
    private const val PERIODIC = "contact-ringtone-periodic"
    private const val ON_CHANGE = "contact-ringtone-on-change"

    fun enable(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS).build()
        )
        armContentTrigger(ctx, fromWorker = false)
    }

    fun disable(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelUniqueWork(PERIODIC)
        wm.cancelUniqueWork(ON_CHANGE)
    }

    fun armContentTrigger(ctx: Context, fromWorker: Boolean) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(ContactsContract.Contacts.CONTENT_URI, true)
            .setTriggerContentUpdateDelay(30, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(2, TimeUnit.MINUTES)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(SyncWorker.KEY_REARM to true))
            .build()
        // From inside the worker, queue the next trigger after the current run so the
        // run's own contact edits don't immediately fire it again.
        val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        WorkManager.getInstance(ctx).enqueueUniqueWork(ON_CHANGE, policy, request)
    }
}
