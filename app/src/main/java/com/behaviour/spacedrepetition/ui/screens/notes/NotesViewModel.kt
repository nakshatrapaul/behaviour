package com.behaviour.spacedrepetition.ui.screens.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.behaviour.spacedrepetition.data.local.entity.Note
import com.behaviour.spacedrepetition.data.repository.AnalyticsRepository
import com.behaviour.spacedrepetition.data.repository.NoteRepository
import com.behaviour.spacedrepetition.scheduling.AlarmScheduler
import com.behaviour.spacedrepetition.scheduling.FibonacciScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val addNoteTitle: String = "",
    val addNoteContent: String = "",
    val showAddDialog: Boolean = false,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    application: Application,
    private val noteRepository: NoteRepository,
    private val alarmScheduler: AlarmScheduler,
    private val analyticsRepository: AnalyticsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _searchQuery.debounce(300).collectLatest { query ->
                if (query.isBlank()) {
                    noteRepository.getAllNotes().collect { notes ->
                        _uiState.value = _uiState.value.copy(notes = notes, isLoading = false)
                    }
                } else {
                    noteRepository.searchNotes(query).collect { notes ->
                        _uiState.value = _uiState.value.copy(notes = notes, isLoading = false)
                    }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        _searchQuery.value = query
    }

    fun onTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(addNoteTitle = title)
    }

    fun onContentChanged(content: String) {
        _uiState.value = _uiState.value.copy(addNoteContent = content)
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            addNoteTitle = "",
            addNoteContent = "",
            saveSuccess = false
        )
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun saveNote() {
        val state = _uiState.value
        if (state.addNoteTitle.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val noteId = noteRepository.createNoteWithRevisions(
                title = state.addNoteTitle,
                content = state.addNoteContent
            )

            // Log note creation for analytics
            analyticsRepository.logNoteCreated(noteId, state.addNoteTitle, "note")

            // Schedule the first alarm (5 hours from now)
            val firstRevisionTime = FibonacciScheduler.getRevisionTime(
                createdAt = System.currentTimeMillis(),
                revisionIndex = 0
            )

            alarmScheduler.scheduleRevisionAlarm(
                context = getApplication(),
                revisionId = noteId, // We'll use noteId as a proxy; in real app query the revision
                noteTitle = state.addNoteTitle,
                triggerAtMillis = firstRevisionTime
            )

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                showAddDialog = false,
                addNoteTitle = "",
                addNoteContent = "",
                saveSuccess = true
            )
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            noteRepository.deleteNote(note)
            analyticsRepository.deleteNoteAnalytics(note.id)
        }
    }

    fun resetNoteRevisions(note: Note) {
        viewModelScope.launch {
            val firstRevision = noteRepository.resetNoteRevisions(note.id)
            if (firstRevision != null) {
                alarmScheduler.scheduleRevisionAlarm(
                    context = getApplication(),
                    revisionId = firstRevision.id,
                    noteTitle = note.title,
                    triggerAtMillis = firstRevision.scheduledAt
                )
            }
        }
    }

    fun saveReminder(title: String, scheduledTimeMs: Long) {
        viewModelScope.launch {
            val noteId = noteRepository.createReminder(title, scheduledTimeMs)

            val firstRevision = noteRepository.getNextUncompletedRevisionForNote(noteId)
            if (firstRevision != null) {
                alarmScheduler.scheduleRevisionAlarm(
                    context = getApplication(),
                    revisionId = firstRevision.id,
                    noteTitle = title,
                    triggerAtMillis = scheduledTimeMs,
                    isReminder = true
                )
            }

            // Log note creation in analytics database
            analyticsRepository.logNoteCreated(noteId, title, "event")
        }
    }

    fun clearSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }
}
