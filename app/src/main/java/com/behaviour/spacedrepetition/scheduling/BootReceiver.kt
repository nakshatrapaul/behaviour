package com.behaviour.spacedrepetition.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.behaviour.spacedrepetition.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-schedules all pending revision alarms after device reboot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed — rescheduling revision alarms")

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "behaviour_database"
                ).build()

                val dao = db.noteDao()
                val alarmScheduler = AlarmScheduler()
                val now = System.currentTimeMillis()

                val nextRevision = dao.getNextPendingRevision()
                if (nextRevision != null) {
                    val note = dao.getNoteById(nextRevision.noteId)
                    val triggerAt = if (nextRevision.scheduledAt <= now) {
                        now + 60_000 // If overdue, trigger in 1 minute
                    } else {
                        nextRevision.scheduledAt
                    }
                    alarmScheduler.scheduleRevisionAlarm(
                        context = context.applicationContext,
                        revisionId = nextRevision.id,
                        noteTitle = note?.title ?: "Study Notes",
                        triggerAtMillis = triggerAt
                    )
                }

                db.close()

                // Also restart the periodic worker
                NotificationWorker.schedule(context.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling alarms after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
