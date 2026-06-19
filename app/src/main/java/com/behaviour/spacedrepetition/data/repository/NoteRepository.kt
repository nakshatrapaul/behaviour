package com.behaviour.spacedrepetition.data.repository

import com.behaviour.spacedrepetition.data.local.NoteDao
import com.behaviour.spacedrepetition.data.local.entity.Note
import com.behaviour.spacedrepetition.data.local.entity.Revision
import com.behaviour.spacedrepetition.scheduling.FibonacciScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao
) {
    fun getAllNotes(): Flow<List<Note>> = noteDao.getAllNotes()

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query)

    suspend fun getNoteById(noteId: Long): Note? = noteDao.getNoteById(noteId)

    suspend fun getNoteByTitle(title: String): Note? = noteDao.getNoteByTitle(title)

    fun observeNoteById(noteId: Long): Flow<Note?> = noteDao.observeNoteById(noteId)

    /**
     * Creates a note and generates all Fibonacci-spaced revision records.
     * Returns the new note ID.
     */
    suspend fun createNoteWithRevisions(title: String, content: String): Long {
        val now = System.currentTimeMillis()
        val note = Note(title = title, content = content, createdAt = now)
        val noteId = noteDao.insertNote(note)

        val revisions = FibonacciScheduler.generateAllRevisions(
            noteId = noteId,
            createdAt = now
        )
        noteDao.insertRevisions(revisions)

        return noteId
    }

    /**
     * Creates a standalone reminder (event) with a single scheduled revision.
     * Returns the new note ID.
     */
    suspend fun createReminder(title: String, scheduledAt: Long): Long {
        val now = System.currentTimeMillis()
        val note = Note(
            title = title,
            content = "Standalone event",
            createdAt = now,
            totalRevisions = 1,
            isReminder = true
        )
        val noteId = noteDao.insertNote(note)

        val revision = Revision(
            noteId = noteId,
            revisionIndex = 0,
            scheduledAt = scheduledAt
        )
        noteDao.insertRevisions(listOf(revision))

        return noteId
    }

    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)

    suspend fun updateNote(note: Note) = noteDao.updateNote(note)

    // ── Revisions ──────────────────────────────────────────

    fun getPendingRevisions(now: Long = System.currentTimeMillis()): Flow<List<Revision>> =
        noteDao.getPendingRevisions(now)

    fun getPendingRevisionCount(now: Long = System.currentTimeMillis()): Flow<Int> =
        noteDao.getPendingRevisionCount(now)

    fun getRevisionsForNote(noteId: Long): Flow<List<Revision>> =
        noteDao.getRevisionsForNote(noteId)

    fun getRevisionsByDate(startOfDay: Long, endOfDay: Long): Flow<List<Revision>> =
        noteDao.getRevisionsByDate(startOfDay, endOfDay)

    fun getPendingRevisionsInRange(startMs: Long, endMs: Long): Flow<List<Revision>> =
        noteDao.getPendingRevisionsInRange(startMs, endMs)

    suspend fun completeRevision(revision: Revision) {
        val now = System.currentTimeMillis()
        noteDao.updateRevision(
            revision.copy(
                isCompleted = true,
                completedAt = now
            )
        )

        // Always reschedule uncompleted revisions relative to the maximum of completion time and the scheduled time of this completed revision
        val baseTime = maxOf(now, revision.scheduledAt)
        rescheduleFutureRevisions(revision.noteId, baseTime)
    }

    private suspend fun rescheduleFutureRevisions(noteId: Long, completedAt: Long) {
        val uncompleted = noteDao.getUncompletedRevisionsForNoteDirect(noteId)
        var lastTime = completedAt
        val updated = uncompleted.mapIndexed { index, rev ->
            val interval = FibonacciScheduler.getRevisionIntervalMs(rev.revisionIndex)
            val adjustedInterval = if (index == 0) {
                // Success: n - n/4
                interval - (interval / 4)
            } else {
                interval
            }
            val newScheduled = lastTime + adjustedInterval
            lastTime = newScheduled
            rev.copy(scheduledAt = newScheduled)
        }
        if (updated.isNotEmpty()) {
            noteDao.updateRevisions(updated)
        }
    }

    suspend fun failRevision(revision: Revision) {
        val now = System.currentTimeMillis()
        val noteId = revision.noteId
        val currentIndex = revision.revisionIndex
        val newIndex = maxOf(0, currentIndex - 1)

        val uncompleted = noteDao.getUncompletedRevisionsForNoteDirect(noteId)
        val baseTime = maxOf(now, revision.scheduledAt)
        var lastTime = baseTime
        val updated = uncompleted.mapIndexed { listIndex, rev ->
            val interval = if (listIndex == 0) {
                if (newIndex == 0) {
                    5L * 60 * 60 * 1000 // 5 hours in ms
                } else {
                    FibonacciScheduler.getRevisionIntervalMs(newIndex)
                }
            } else {
                FibonacciScheduler.getRevisionIntervalMs(rev.revisionIndex)
            }
            val adjustedInterval = if (listIndex == 0) {
                // Failure: n + n/4
                interval + (interval / 4)
            } else {
                interval
            }
            val newScheduled = lastTime + adjustedInterval
            lastTime = newScheduled
            rev.copy(
                scheduledAt = newScheduled
            )
        }
        if (updated.isNotEmpty()) {
            noteDao.updateRevisions(updated)
        }
    }

    fun getNextUncompletedRevisions(): Flow<List<Revision>> =
        noteDao.getNextUncompletedRevisions()

    suspend fun getNextPendingRevision(): Revision? = noteDao.getNextPendingRevision()

    suspend fun getRevisionById(revisionId: Long): Revision? = noteDao.getRevisionById(revisionId)

    fun getDaysWithPendingRevisions(startMs: Long, endMs: Long): Flow<List<Long>> =
        noteDao.getDaysWithPendingRevisions(startMs, endMs)

    suspend fun getNextUncompletedRevisionForNote(noteId: Long): Revision? =
        noteDao.getNextUncompletedRevisionForNote(noteId)

    suspend fun resetNoteRevisions(noteId: Long): Revision? {
        noteDao.deleteAllRevisionsForNote(noteId)
        val now = System.currentTimeMillis()
        val revisions = FibonacciScheduler.generateAllRevisions(
            noteId = noteId,
            createdAt = now,
            count = 400
        )
        noteDao.insertRevisions(revisions)
        return noteDao.getAllRevisionsForNoteDirect(noteId).find { it.revisionIndex == 0 }
    }

    suspend fun alignNotesTo400Revisions() {
        val allNotes = noteDao.getAllNotesDirect()
        for (note in allNotes) {
            if (note.totalRevisions != 400) {
                // 1. Update the note itself to 400 revisions
                val updatedNote = note.copy(totalRevisions = 400)
                noteDao.updateNote(updatedNote)

                // 2. Adjust the revisions
                val allRevisions = noteDao.getAllRevisionsForNoteDirect(note.id)
                val completed = allRevisions.filter { it.isCompleted }.sortedBy { it.revisionIndex }

                // The number of completed revisions
                val completedCount = completed.size

                // Delete all existing uncompleted revisions for this note
                noteDao.deleteUncompletedRevisionsForNote(note.id)

                // Generate new uncompleted revisions starting from completedCount to 399
                val now = System.currentTimeMillis()
                var lastTime = now
                val newRevisions = (completedCount until 400).map { index ->
                    val interval = if (index == completedCount) {
                        if (index == 0) 5L * 60 * 60 * 1000 else FibonacciScheduler.getRevisionIntervalMs(index)
                    } else {
                        FibonacciScheduler.getRevisionIntervalMs(index)
                    }
                    val scheduled = lastTime + interval
                    lastTime = scheduled
                    Revision(
                        noteId = note.id,
                        revisionIndex = index,
                        scheduledAt = scheduled
                    )
                }
                noteDao.insertRevisions(newRevisions)
            }
        }
    }
}
