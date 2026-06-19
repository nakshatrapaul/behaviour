package com.behaviour.spacedrepetition.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.behaviour.spacedrepetition.data.local.entity.Note
import com.behaviour.spacedrepetition.data.local.entity.Revision
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // ── Notes ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("SELECT * FROM notes WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Long): Note?

    @Query("SELECT * FROM notes WHERE title = :title LIMIT 1")
    suspend fun getNoteByTitle(title: String): Note?

    @Query("SELECT * FROM notes WHERE id = :noteId")
    fun observeNoteById(noteId: Long): Flow<Note?>

    @Query("""
        SELECT * FROM notes WHERE title LIKE '%' || :query || '%' 
        OR content LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
    """)
    fun searchNotes(query: String): Flow<List<Note>>

    // ── Revisions ──────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRevisions(revisions: List<Revision>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRevision(revision: Revision)

    @Update
    suspend fun updateRevision(revision: Revision)

    @Query("SELECT * FROM revisions WHERE noteId = :noteId ORDER BY revisionIndex ASC")
    fun getRevisionsForNote(noteId: Long): Flow<List<Revision>>

    @Query("""
        SELECT * FROM revisions 
        WHERE isCompleted = 0 AND scheduledAt <= :now 
        ORDER BY scheduledAt ASC
    """)
    fun getPendingRevisions(now: Long): Flow<List<Revision>>

    @Query("""
        SELECT COUNT(*) FROM revisions 
        WHERE isCompleted = 0 AND scheduledAt <= :now
    """)
    fun getPendingRevisionCount(now: Long): Flow<Int>

    @Query("""
        SELECT * FROM revisions 
        WHERE scheduledAt >= :startOfDay AND scheduledAt < :endOfDay 
        ORDER BY scheduledAt ASC
    """)
    fun getRevisionsByDate(startOfDay: Long, endOfDay: Long): Flow<List<Revision>>

    @Query("""
        SELECT * FROM revisions 
        WHERE scheduledAt >= :startMs AND scheduledAt < :endMs AND isCompleted = 0
        ORDER BY scheduledAt ASC
    """)
    fun getPendingRevisionsInRange(startMs: Long, endMs: Long): Flow<List<Revision>>

    @Query("""
        SELECT * FROM revisions 
        WHERE isCompleted = 0 
        ORDER BY scheduledAt ASC 
        LIMIT 1
    """)
    suspend fun getNextPendingRevision(): Revision?

    @Query("SELECT * FROM revisions WHERE id = :revisionId")
    suspend fun getRevisionById(revisionId: Long): Revision?

    // ── Combined ───────────────────────────────────────────

    @Query("""
        SELECT DISTINCT noteId FROM revisions 
        WHERE scheduledAt >= :startOfDay AND scheduledAt < :endOfDay AND isCompleted = 0
    """)
    fun getNoteIdsWithPendingRevisionsOnDate(startOfDay: Long, endOfDay: Long): Flow<List<Long>>

    @Query("""
        SELECT DISTINCT scheduledAt / 86400000 as dayEpoch FROM revisions 
        WHERE scheduledAt >= :startMs AND scheduledAt < :endMs AND isCompleted = 0
    """)
    fun getDaysWithPendingRevisions(startMs: Long, endMs: Long): Flow<List<Long>>

    @Query("""
        SELECT r1.* FROM revisions r1
        INNER JOIN (
            SELECT noteId, MIN(revisionIndex) as minIndex
            FROM revisions
            WHERE isCompleted = 0
            GROUP BY noteId
        ) r2 ON r1.noteId = r2.noteId AND r1.revisionIndex = r2.minIndex
        WHERE r1.isCompleted = 0
        ORDER BY r1.scheduledAt ASC
    """)
    fun getNextUncompletedRevisions(): Flow<List<Revision>>

    @Query("SELECT * FROM revisions WHERE noteId = :noteId AND isCompleted = 0 ORDER BY revisionIndex ASC")
    suspend fun getUncompletedRevisionsForNoteDirect(noteId: Long): List<Revision>

    @Query("SELECT * FROM notes WHERE isArchived = 0")
    suspend fun getAllNotesDirect(): List<Note>

    @Query("SELECT * FROM revisions WHERE noteId = :noteId ORDER BY revisionIndex ASC")
    suspend fun getAllRevisionsForNoteDirect(noteId: Long): List<Revision>

    @Query("DELETE FROM revisions WHERE noteId = :noteId AND isCompleted = 0")
    suspend fun deleteUncompletedRevisionsForNote(noteId: Long)

    @Query("DELETE FROM revisions WHERE noteId = :noteId")
    suspend fun deleteAllRevisionsForNote(noteId: Long)

    @Query("""
        SELECT * FROM revisions 
        WHERE noteId = :noteId AND isCompleted = 0 
        ORDER BY revisionIndex ASC 
        LIMIT 1
    """)
    suspend fun getNextUncompletedRevisionForNote(noteId: Long): Revision?

    @Update
    suspend fun updateRevisions(revisions: List<Revision>)
}
