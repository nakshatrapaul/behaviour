package com.behaviour.spacedrepetition.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.behaviour.spacedrepetition.MainActivity
import com.behaviour.spacedrepetition.R
import com.behaviour.spacedrepetition.data.local.AppDatabase
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that checks for overdue revisions
 * every 4 hours and sends a summary notification.
 * Acts as a safety net in case AlarmManager alarms are missed.
 */
class NotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "NotificationWorker"
        private const val WORK_NAME = "revision_check_worker"
        private const val SUMMARY_NOTIFICATION_ID = 99999
        private const val CHANNEL_ID = "revision_summary"
        private const val CHANNEL_NAME = "Revision Summary"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
                4, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Scheduled periodic revision check worker")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val db = androidx.room.Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "behaviour_database"
            ).build()

            val dao = db.noteDao()
            val now = System.currentTimeMillis()
            val nextRevision = dao.getNextPendingRevision()

            if (nextRevision != null && nextRevision.scheduledAt <= now) {
                // Count all pending
                // We can't easily collect a Flow here, so do a direct query approach
                showSummaryNotification(applicationContext)
            }

            db.close()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking revisions", e)
            Result.retry()
        }
    }

    private fun showSummaryNotification(context: Context) {
        createNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "revision")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📖 You have pending revisions!")
            .setContentText("Don't break your streak! Open Behave to review your notes.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SUMMARY_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Periodic reminders about pending revisions"
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
