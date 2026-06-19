package com.behaviour.spacedrepetition.ui.screens.home

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

data class HomeUiState(
    val pendingRevisionCount: Int = 0,
    val recentNotes: List<Note> = emptyList(),
    val todayRevisions: List<Revision> = emptyList(),
    val totalNotes: Int = 0,
    val greeting: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val noteRepository: NoteRepository,
    private val alarmScheduler: AlarmScheduler,
    private val analyticsRepository: AnalyticsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val now = System.currentTimeMillis()

        // Observe pending revision count
        viewModelScope.launch {
            noteRepository.getPendingRevisionCount(now).collect { count ->
                _uiState.value = _uiState.value.copy(pendingRevisionCount = count)
            }
        }

        // Observe recent notes
        viewModelScope.launch {
            noteRepository.getAllNotes().collect { notes ->
                _uiState.value = _uiState.value.copy(
                    recentNotes = notes.take(5),
                    totalNotes = notes.size
                )
            }
        }

        // Set greeting based on time of day
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good Morning ☀️"
            hour < 17 -> "Good Afternoon 🌤️"
            else -> "Good Evening 🌙"
        }
        _uiState.value = _uiState.value.copy(greeting = greeting)
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
}
