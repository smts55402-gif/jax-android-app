package com.jax.automation.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jax.automation.JaxApplication

/**
 * One-shot worker fired on app start (and on demand) so an interrupted queue
 * is recovered even if the process died. The executor itself marks
 * interrupted tasks PAUSED — recovery never assumes success.
 */
class QueueResumeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            val app = applicationContext as JaxApplication
            app.container.executor.restoreAfterRestart()
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            ListenableWorker.Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "jax-queue-resume"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<QueueResumeWorker>().build()
            )
        }
    }
}
