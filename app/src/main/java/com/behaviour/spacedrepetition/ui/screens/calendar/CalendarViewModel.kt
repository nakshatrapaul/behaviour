package com.behaviour.spacedrepetition.ui.screens.calendar

import android.app.Application
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.behaviour.spacedrepetition.data.local.entity.Note
import com.behaviour.spacedrepetition.data.local.entity.Revision
import com.behaviour.spacedrepetition.data.repository.NoteRepository
import com.behaviour.spacedrepetition.scheduling.AlarmScheduler
import com.behaviour.spacedrepetition.data.repository.AnalyticsRepository
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class CalendarViewMode {
    LIST, CALENDAR
}

data class CalendarDayData(
    val pendingCount: Int = 0,
    val durationMinutes: Int = 0,
    val hasReminder: Boolean = false
)

data class CalendarRevisionItem(
    val revision: Revision,
    val note: Note?,
    val nextRevision: Revision? = null
)

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val daysWithRevisions: Set<LocalDate> = emptySet(),
    val selectedDate: LocalDate? = null,
    val selectedDateRevisions: List<CalendarRevisionItem> = emptyList(),
    val isLoading: Boolean = false,
    val viewMode: CalendarViewMode = CalendarViewMode.CALENDAR,
    val totalPendingCount: Int = 0,
    val monthData: Map<LocalDate, CalendarDayData> = emptyMap(),
    val allPendingRevisions: List<CalendarRevisionItem> = emptyList()
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    application: Application,
    private val noteRepository: NoteRepository,
    private val alarmScheduler: AlarmScheduler,
    private val analyticsRepository: AnalyticsRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            noteRepository.getPendingRevisionCount().collect { count ->
                _uiState.value = _uiState.value.copy(totalPendingCount = count)
            }
        }
        viewModelScope.launch {
            noteRepository.getPendingRevisions().collect { revisions ->
                val items = revisions.map { revision ->
                    val note = noteRepository.getNoteById(revision.noteId)
                    val nextRevision = if (revision.isCompleted) {
                        noteRepository.getNextUncompletedRevisionForNote(revision.noteId)
                    } else null
                    CalendarRevisionItem(revision, note, nextRevision)
                }
                _uiState.value = _uiState.value.copy(allPendingRevisions = items)
            }
        }
        loadMonthData()
        selectDate(LocalDate.now())
    }

    fun setViewMode(mode: CalendarViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }

    fun previousMonth() {
        _uiState.value = _uiState.value.copy(
            currentMonth = _uiState.value.currentMonth.minusMonths(1),
            selectedDate = null,
            selectedDateRevisions = emptyList()
        )
        loadMonthData()
    }

    fun nextMonth() {
        _uiState.value = _uiState.value.copy(
            currentMonth = _uiState.value.currentMonth.plusMonths(1),
            selectedDate = null,
            selectedDateRevisions = emptyList()
        )
        loadMonthData()
    }

    fun selectDate(date: LocalDate) {
        val targetMonth = YearMonth.from(date)
        if (targetMonth != _uiState.value.currentMonth) {
            _uiState.value = _uiState.value.copy(
                currentMonth = targetMonth,
                selectedDate = date
            )
            loadMonthData()
        } else {
            _uiState.value = _uiState.value.copy(selectedDate = date)
        }
        loadDateRevisions(date)
    }

    private var monthDataJob: kotlinx.coroutines.Job? = null

    private fun loadMonthData() {
        monthDataJob?.cancel()
        val month = _uiState.value.currentMonth
        val zone = ZoneId.systemDefault()
        val startMs = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        monthDataJob = viewModelScope.launch {
            combine(
                noteRepository.getPendingRevisionsInRange(startMs, endMs),
                noteRepository.getAllNotes()
            ) { revisions, notes ->
                val notesMap = notes.associateBy { it.id }
                val daysSet = mutableSetOf<LocalDate>()
                val monthDataMap = mutableMapOf<LocalDate, CalendarDayData>()

                val grouped = revisions.groupBy { revision ->
                    java.time.Instant.ofEpochMilli(revision.scheduledAt)
                        .atZone(zone)
                        .toLocalDate()
                }

                grouped.forEach { (date, revisionList) ->
                    daysSet.add(date)
                    val pendingCount = revisionList.size
                    var totalDuration = 0
                    var hasReminder = false
                    revisionList.forEach { revision ->
                        val note = notesMap[revision.noteId]
                        if (note != null) {
                            totalDuration += estimateNoteDuration(note.content)
                            if (note.isReminder) {
                                hasReminder = true
                            }
                        }
                    }
                    monthDataMap[date] = CalendarDayData(
                        pendingCount = pendingCount,
                        durationMinutes = totalDuration,
                        hasReminder = hasReminder
                    )
                }
                Pair(daysSet, monthDataMap)
            }.collect { (daysSet, monthDataMap) ->
                _uiState.value = _uiState.value.copy(
                    daysWithRevisions = daysSet,
                    monthData = monthDataMap
                )
            }
        }
    }

    private fun estimateNoteDuration(content: String): Int {
        // 1. Text-based duration (excluding attachment tags so we don't double count)
        val tagRegex = Regex("""!\[\[(.*?)]]""")
        val cleanContent = content.replace(tagRegex, "")
        
        val len = cleanContent.length
        var duration = when {
            len < 40 -> 0
            len < 50 -> 1
            len < 100 -> 10
            else -> 10 + (len - 100) / 50
        }

        // 2. Parse attachments
        tagRegex.findAll(content).forEach { matchResult ->
            val fileName = matchResult.groups[1]?.value ?: ""
            val lowerName = fileName.lowercase()
            if (lowerName.endsWith(".pdf")) {
                val pages = getPdfPageCount(fileName)
                duration += pages // 1 minute per page
            } else if (lowerName.endsWith(".jpg") ||
                       lowerName.endsWith(".jpeg") ||
                       lowerName.endsWith(".png") ||
                       lowerName.endsWith(".webp")) {
                duration += 1 // 1 minute per image
            }
        }

        return duration
    }

    private fun getPdfPageCount(fileName: String): Int {
        return try {
            val attachmentsDir = File(getApplication<Application>().filesDir, "attachments")
            val file = File(attachmentsDir, fileName)
            if (!file.exists()) return 0
            
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            renderer.close()
            pfd.close()
            pageCount
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    private var dateRevisionsJob: kotlinx.coroutines.Job? = null

    private fun loadDateRevisions(date: LocalDate) {
        dateRevisionsJob?.cancel()
        val zone = ZoneId.systemDefault()
        val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        dateRevisionsJob = viewModelScope.launch {
            noteRepository.getRevisionsByDate(startMs, endMs).collect { revisions ->
                val items = revisions.map { revision ->
                    val note = noteRepository.getNoteById(revision.noteId)
                    val nextRevision = if (revision.isCompleted) {
                        noteRepository.getNextUncompletedRevisionForNote(revision.noteId)
                    } else null
                    CalendarRevisionItem(revision, note, nextRevision)
                }
                _uiState.value = _uiState.value.copy(selectedDateRevisions = items)
            }
        }
    }

    fun completeRevision(revision: Revision, note: Note, recalled: Boolean) {
        viewModelScope.launch {
            if (recalled) {
                noteRepository.completeRevision(revision)
            } else {
                noteRepository.failRevision(revision)
            }

            // Schedule the next alarm for this note's upcoming revision
            val nextRevision = noteRepository.getNextPendingRevision()
            if (nextRevision != null) {
                val nextNote = noteRepository.getNoteById(nextRevision.noteId)
                if (nextNote != null) {
                    val triggerAt = if (nextRevision.scheduledAt <= System.currentTimeMillis()) {
                        System.currentTimeMillis() + 60_000
                    } else {
                        nextRevision.scheduledAt
                    }
                    alarmScheduler.scheduleRevisionAlarm(
                        context = getApplication(),
                        revisionId = nextRevision.id,
                        noteTitle = nextNote.title,
                        triggerAtMillis = triggerAt,
                        isReminder = nextNote.isReminder
                    )
                }
            }
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

    fun onReviseClick(revision: Revision, context: android.content.Context, onNavigate: (Long) -> Unit) {
        viewModelScope.launch {
            val firstUncompleted = noteRepository.getNextUncompletedRevisionForNote(revision.noteId)
            if (firstUncompleted != null && firstUncompleted.id == revision.id) {
                onNavigate(revision.id)
            } else {
                android.widget.Toast.makeText(
                    context,
                    "first complete the latest ones",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
