package com.behaviour.spacedrepetition.ui.screens.revision

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.behaviour.spacedrepetition.data.local.entity.Note
import com.behaviour.spacedrepetition.data.local.entity.Revision
import com.behaviour.spacedrepetition.data.repository.AnalyticsRepository
import com.behaviour.spacedrepetition.data.repository.NoteRepository
import com.behaviour.spacedrepetition.scheduling.AlarmScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RevisionItem(
    val revision: Revision,
    val note: Note
)

data class RevisionUiState(
    val pendingItems: List<RevisionItem> = emptyList(),
    val isLoading: Boolean = true,
    val completedMessage: String? = null
)

@HiltViewModel
class RevisionViewModel @Inject constructor(
    application: Application,
    private val noteRepository: NoteRepository,
    private val alarmScheduler: AlarmScheduler,
    private val analyticsRepository: AnalyticsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RevisionUiState())
    val uiState: StateFlow<RevisionUiState> = _uiState.asStateFlow()

    init {
        loadPendingRevisions()
    }

    private fun loadPendingRevisions() {
        viewModelScope.launch {
            noteRepository.getNextUncompletedRevisions().collect { revisions ->
                val items = revisions.mapNotNull { revision ->
                    val note = noteRepository.getNoteById(revision.noteId)
                    if (note != null) RevisionItem(revision, note) else null
                }
                _uiState.value = _uiState.value.copy(
                    pendingItems = items,
                    isLoading = false
                )
            }
        }
    }

    fun completeRevision(revisionItem: RevisionItem, recalled: Boolean) {
        viewModelScope.launch {
            if (recalled) {
                noteRepository.completeRevision(revisionItem.revision)
                _uiState.value = _uiState.value.copy(
                    completedMessage = "✅ \"${revisionItem.note.title}\" revision ${revisionItem.revision.revisionIndex + 1} completed!"
                )
            } else {
                noteRepository.failRevision(revisionItem.revision)
                _uiState.value = _uiState.value.copy(
                    completedMessage = "❌ \"${revisionItem.note.title}\" recall failed. Resetting schedule!"
                )
            }

            // Log revision attempt for analytics
            analyticsRepository.logRevisionAttempt(
                noteId = revisionItem.note.id,
                noteTitle = revisionItem.note.title,
                revisionIndex = revisionItem.revision.revisionIndex + 1,
                status = if (recalled) "recalled" else "failed"
            )

            // Schedule the next alarm for this note's upcoming revision
            val nextRevision = noteRepository.getNextPendingRevision()
            if (nextRevision != null) {
                val note = noteRepository.getNoteById(nextRevision.noteId)
                if (note != null) {
                    val triggerAt = if (nextRevision.scheduledAt <= System.currentTimeMillis()) {
                        System.currentTimeMillis() + 60_000
                    } else {
                        nextRevision.scheduledAt
                    }
                    alarmScheduler.scheduleRevisionAlarm(
                        context = getApplication(),
                        revisionId = nextRevision.id,
                        noteTitle = note.title,
                        triggerAtMillis = triggerAt,
                        isReminder = note.isReminder
                    )
                }
            }
        }
    }

    fun updateNoteContent(note: Note, newTitle: String, newContent: String) {
        viewModelScope.launch {
            val updatedNote = note.copy(title = newTitle, content = newContent)
            noteRepository.updateNote(updatedNote)
        }
    }

    fun deleteReminder(note: Note) {
        viewModelScope.launch {
            // Cancel scheduled alarm if active
            val revision = noteRepository.getNextUncompletedRevisionForNote(note.id)
            if (revision != null) {
                alarmScheduler.cancelAlarm(getApplication(), revision.id)
            }
            // Delete local note and revisions
            noteRepository.deleteNote(note)
            // Delete from Appwrite analytics
            analyticsRepository.deleteNoteAnalytics(note.id)
        }
    }

    fun clearCompletedMessage() {
        _uiState.value = _uiState.value.copy(completedMessage = null)
    }
}
