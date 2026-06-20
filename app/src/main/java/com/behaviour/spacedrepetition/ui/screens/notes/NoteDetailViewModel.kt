package com.behaviour.spacedrepetition.ui.screens.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.behaviour.spacedrepetition.data.local.entity.Note
import com.behaviour.spacedrepetition.data.repository.NoteRepository
import com.behaviour.spacedrepetition.data.repository.AnalyticsRepository
import com.behaviour.spacedrepetition.scheduling.AlarmScheduler
import com.behaviour.spacedrepetition.scheduling.FibonacciScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.behaviour.spacedrepetition.data.repository.BillingRepository
import kotlinx.coroutines.flow.first

data class NoteDetailUiState(
    val title: String = "",
    val content: String = "",
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val saveSuccess: Boolean = false,
    val noteExists: Boolean = false,
    val showPremiumDialog: Boolean = false
)

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val noteRepository: NoteRepository,
    private val alarmScheduler: AlarmScheduler,
    private val analyticsRepository: AnalyticsRepository,
    private val billingRepository: BillingRepository,
    application: Application
) : AndroidViewModel(application) {

    val noteId: Long = savedStateHandle.get<String>("noteId")?.toLongOrNull() ?: 0L

    private val _uiState = MutableStateFlow(NoteDetailUiState())
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    init {
        loadNote()
    }

    private fun loadNote() {
        if (noteId > 0) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val note = noteRepository.getNoteById(noteId)
                if (note != null) {
                    _uiState.value = NoteDetailUiState(
                        title = note.title,
                        content = note.content,
                        isEditMode = false,
                        noteExists = true
                    )
                } else {
                    // Note not found in DB (fallback to new)
                    _uiState.value = NoteDetailUiState(isEditMode = true, noteExists = false)
                }
            }
        } else {
            // New note creation
            _uiState.value = NoteDetailUiState(isEditMode = true, noteExists = false)
        }
    }

    fun onTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun onContentChanged(content: String) {
        _uiState.value = _uiState.value.copy(content = content)
    }

    fun toggleEditMode() {
        val current = _uiState.value
        if (current.isEditMode) {
            // Saving changes when exiting edit mode
            saveNote()
        } else {
            _uiState.value = current.copy(isEditMode = true)
        }
    }

    fun dismissPremiumDialog() {
        _uiState.value = _uiState.value.copy(showPremiumDialog = false)
    }

    fun saveNote(onComplete: (() -> Unit)? = null) {
        val state = _uiState.value
        if (state.title.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            if (noteId > 0) {
                // Update existing note
                val existingNote = noteRepository.getNoteById(noteId)
                if (existingNote != null) {
                    val updatedNote = existingNote.copy(
                        title = state.title,
                        content = state.content
                    )
                    noteRepository.updateNote(updatedNote)
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isEditMode = false,
                    saveSuccess = true
                )
            } else {
                // Check free note limit (max 5 notes)
                if (!billingRepository.isPremium()) {
                    val count = noteRepository.getAllNotes().first().size
                    if (count >= 5) {
                        _uiState.value = _uiState.value.copy(
                            showPremiumDialog = true,
                            isLoading = false
                        )
                        return@launch
                    }
                }

                // Create new note with revisions
                val newId = noteRepository.createNoteWithRevisions(
                    title = state.title,
                    content = state.content
                )

                // Log note creation for analytics
                analyticsRepository.logNoteCreated(newId, state.title, "note")

                // Schedule alarm using the first revision's actual ID
                val firstRevision = noteRepository.getNextUncompletedRevisionForNote(newId)
                if (firstRevision != null) {
                    alarmScheduler.scheduleRevisionAlarm(
                        context = getApplication(),
                        revisionId = firstRevision.id,
                        noteTitle = state.title,
                        triggerAtMillis = firstRevision.scheduledAt
                    )
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isEditMode = false,
                    saveSuccess = true
                )
                
                onComplete?.invoke()
            }
        }
    }

    fun resolveWikiLink(title: String, onNavigateToNote: (Long) -> Unit) {
        viewModelScope.launch {
            val existingNote = noteRepository.getNoteByTitle(title)
            if (existingNote != null) {
                onNavigateToNote(existingNote.id)
            } else {
                // Autocreate the note in the background (like Obsidian does)
                val newId = noteRepository.createNoteWithRevisions(
                    title = title,
                    content = "# $title\n\nWrite your note content here..."
                )

                // Log note creation for analytics
                analyticsRepository.logNoteCreated(newId, title, "note")

                // Schedule alarm for autocreated note using the first revision's actual ID
                val firstRevision = noteRepository.getNextUncompletedRevisionForNote(newId)
                if (firstRevision != null) {
                    alarmScheduler.scheduleRevisionAlarm(
                        context = getApplication(),
                        revisionId = firstRevision.id,
                        noteTitle = title,
                        triggerAtMillis = firstRevision.scheduledAt
                    )
                }

                onNavigateToNote(newId)
            }
        }
    }
}
