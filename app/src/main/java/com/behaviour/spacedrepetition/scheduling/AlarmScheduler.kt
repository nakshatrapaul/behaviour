package com.behaviour.spacedrepetition.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.behaviour.spacedrepetition.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor() {

    companion object {
        private const val TAG = "AlarmScheduler"
        const val EXTRA_REVISION_ID = "extra_revision_id"
        const val EXTRA_NOTE_TITLE = "extra_note_title"
        private const val REQUEST_CODE_BASE = 10000
    }

    fun scheduleRevisionAlarm(
        context: Context,
        revisionId: Long,
        noteTitle: String,
        triggerAtMillis: Long,
        isReminder: Boolean = false
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_REVISION_ID, revisionId)
            putExtra(EXTRA_NOTE_TITLE, noteTitle)
            putExtra("extra_is_reminder", isReminder)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (REQUEST_CODE_BASE + revisionId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val showAppIntent = Intent(context, MainActivity::class.java)
            val showAppPendingIntent = PendingIntent.getActivity(
                context,
                (REQUEST_CODE_BASE + revisionId).toInt(),
                showAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showAppPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Scheduled exact alarm clock for revision $revisionId at $triggerAtMillis")
        } catch (e: Exception) {
            Log.w(TAG, "Cannot schedule alarm clock, falling back to setAndAllowWhileIdle", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancelAlarm(context: Context, revisionId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (REQUEST_CODE_BASE + revisionId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
