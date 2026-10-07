/* SPDX-License-Identifier: MPL-2.0 */
package org.mozilla.reference.browser.ani

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Refresh already-pinned artwork after an update, including when the app is closed. */
class ShortcutIconRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "refresh-pinned-site-icons",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ShortcutIconRefreshWorker>().build(),
            )
        }
    }
}

class ShortcutIconRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val complete = try { WebsiteShortcut.refreshPinned(applicationContext) } catch (_: java.io.IOException) { false }
        if (complete || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}
