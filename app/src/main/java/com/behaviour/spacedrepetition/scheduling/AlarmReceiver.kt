package com.behaviour.spacedrepetition.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.behaviour.spacedrepetition.MainActivity
import com.behaviour.spacedrepetition.R

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "revision_reminders"
        const val CHANNEL_NAME = "Revision Reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val revisionId = intent.getLongExtra(AlarmScheduler.EXTRA_REVISION_ID, -1)
        val noteTitle = intent.getStringExtra(AlarmScheduler.EXTRA_NOTE_TITLE) ?: "a note"
        val isReminder = intent.getBooleanExtra("extra_is_reminder", false)

        createNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", if (isReminder) "calendar" else "revision")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            revisionId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleText = if (isReminder) "⏰ Reminder!" else "📚 Time to Revise!"
        val contentText = if (isReminder) noteTitle else "Review your notes: \"$noteTitle\""
        val bigText = if (isReminder) "Reminder: $noteTitle" else "Your spaced repetition schedule says it's time to review \"$noteTitle\". Tap to start your revision session!"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(revisionId.toInt(), notification)
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Reminders for spaced repetition revisions"
            enableVibration(true)
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
