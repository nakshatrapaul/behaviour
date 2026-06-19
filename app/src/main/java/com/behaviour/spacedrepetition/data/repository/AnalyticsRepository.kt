package com.behaviour.spacedrepetition.data.repository

import android.util.Log
import com.behaviour.spacedrepetition.auth.AppwriteClient
import com.behaviour.spacedrepetition.auth.AuthRepository
import io.appwrite.ID
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepository @Inject constructor(
    private val authRepository: AuthRepository
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "AnalyticsRepository"

        const val DATABASE_ID = "analytics_db"
        const val NOTES_COLLECTION_ID = "notes_analytics"
        const val REVISIONS_COLLECTION_ID = "revisions_analytics"
    }

    /**
     * Log note or event creation to Appwrite Database.
     */
    fun logNoteCreated(noteId: Long, title: String, type: String) {
        repositoryScope.launch {
            val currentUser = authRepository.getCurrentUser() ?: return@launch
            val userId = currentUser.email

            try {
                val data = mapOf(
                    "userId" to userId,
                    "noteId" to noteId.toString(),
                    "title" to title,
                    "type" to type
                )

                AppwriteClient.databases.createDocument(
                    databaseId = DATABASE_ID,
                    collectionId = NOTES_COLLECTION_ID,
                    documentId = ID.unique(),
                    data = data
                )
                Log.d(TAG, "Successfully logged note/event creation to Appwrite: $title ($type)")
            } catch (e: AppwriteException) {
                Log.e(TAG, "Appwrite error logging note creation: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error logging note creation: ${e.message}", e)
            }
        }
    }

    /**
     * Log revision attempt (recalled or failed/skipped) to Appwrite Database.
     */
    fun logRevisionAttempt(noteId: Long, noteTitle: String, revisionIndex: Int, status: String) {
        repositoryScope.launch {
            val currentUser = authRepository.getCurrentUser() ?: return@launch
            val userId = currentUser.email

            try {
                val data = mapOf(
                    "userId" to userId,
                    "noteId" to noteId.toString(),
                    "title" to noteTitle,
                    "revisionIndex" to revisionIndex,
                    "status" to status // "recalled" or "failed"
                )

                AppwriteClient.databases.createDocument(
                    databaseId = DATABASE_ID,
                    collectionId = REVISIONS_COLLECTION_ID,
                    documentId = ID.unique(),
                    data = data
                )
                Log.d(TAG, "Successfully logged revision attempt to Appwrite: Note ID $noteId, Status: $status")
            } catch (e: AppwriteException) {
                Log.e(TAG, "Appwrite error logging revision attempt: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error logging revision attempt: ${e.message}", e)
            }
        }
    }

    /**
     * Delete all analytics data associated with a note.
     */
    fun deleteNoteAnalytics(noteId: Long) {
        repositoryScope.launch {
            val noteIdStr = noteId.toString()

            // 1. Delete from notes_analytics
            try {
                val response = AppwriteClient.databases.listDocuments(
                    databaseId = DATABASE_ID,
                    collectionId = NOTES_COLLECTION_ID,
                    queries = listOf(io.appwrite.Query.equal("noteId", noteIdStr))
                )
                for (doc in response.documents) {
                    AppwriteClient.databases.deleteDocument(
                        databaseId = DATABASE_ID,
                        collectionId = NOTES_COLLECTION_ID,
                        documentId = doc.id
                    )
                }
                Log.d(TAG, "Deleted note $noteIdStr analytics from notes_analytics")
            } catch (e: AppwriteException) {
                Log.e(TAG, "Appwrite error deleting note $noteIdStr from notes_analytics: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error deleting note $noteIdStr from notes_analytics: ${e.message}", e)
            }

            // 2. Delete from revisions_analytics
            try {
                val response = AppwriteClient.databases.listDocuments(
                    databaseId = DATABASE_ID,
                    collectionId = REVISIONS_COLLECTION_ID,
                    queries = listOf(io.appwrite.Query.equal("noteId", noteIdStr))
                )
                for (doc in response.documents) {
                    AppwriteClient.databases.deleteDocument(
                        databaseId = DATABASE_ID,
                        collectionId = REVISIONS_COLLECTION_ID,
                        documentId = doc.id
                    )
                }
                Log.d(TAG, "Deleted note $noteIdStr analytics from revisions_analytics")
            } catch (e: AppwriteException) {
                Log.e(TAG, "Appwrite error deleting note $noteIdStr from revisions_analytics: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error deleting note $noteIdStr from revisions_analytics: ${e.message}", e)
            }
        }
    }
}
