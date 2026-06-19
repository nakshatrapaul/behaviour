package com.behaviour.spacedrepetition.ui.screens.calendar

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behaviour.spacedrepetition.ui.theme.*
import com.behaviour.spacedrepetition.data.local.entity.Revision
import com.behaviour.spacedrepetition.data.local.entity.Note
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel(),
    onAddNoteClick: () -> Unit = {},
    onNavigateToRevisionNote: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        item {
            // "Review schedule" title & pending count at the top
            Text(
                text = "Review schedule",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${uiState.totalPendingCount} notes pending",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Segmented Tab Control: List vs Calendar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (uiState.viewMode == CalendarViewMode.LIST) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { viewModel.setViewMode(CalendarViewMode.LIST) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "List",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.viewMode == CalendarViewMode.LIST) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (uiState.viewMode == CalendarViewMode.CALENDAR) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { viewModel.setViewMode(CalendarViewMode.CALENDAR) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Calendar",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.viewMode == CalendarViewMode.CALENDAR) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (uiState.viewMode == CalendarViewMode.LIST) {
            if (uiState.allPendingRevisions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "All caught up! No pending revisions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(uiState.allPendingRevisions) { item ->
                    val revision = item.revision
                    val note = item.note
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (note?.isReminder == true) Color(0xFFFFD54F)
                                        else CalendarDotPending
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note?.title ?: "Unknown Note",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val statusText = if (revision.scheduledAt > System.currentTimeMillis()) "Upcoming" else "Pending"
                                Text(
                                    text = "Revision ${revision.revisionIndex + 1} • $statusText • Scheduled: ${formatDateTime(revision.scheduledAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (note != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                if (note.isReminder) {
                                    FilledTonalButton(
                                        onClick = { viewModel.deleteReminder(note) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Delete now",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = { viewModel.onReviseClick(revision, context, onNavigateToRevisionNote) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = AccentGreen.copy(alpha = 0.15f),
                                            contentColor = AccentGreen
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Revise now",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Calendar View Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Month Navigation
                        MonthNavigator(
                            currentMonth = uiState.currentMonth,
                            onPrevious = viewModel::previousMonth,
                            onNext = viewModel::nextMonth,
                            onTodayClick = { viewModel.selectDate(LocalDate.now()) },
                            onAddNoteClick = onAddNoteClick
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Day of Week Headers
                        DayOfWeekHeader()

                        Spacer(modifier = Modifier.height(8.dp))

                        // Calendar Grid
                        CalendarGrid(
                            currentMonth = uiState.currentMonth,
                            daysWithRevisions = uiState.daysWithRevisions,
                            monthData = uiState.monthData,
                            selectedDate = uiState.selectedDate,
                            onDateSelected = viewModel::selectDate
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Legend
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LegendItem(color = CalendarDotToday, label = "Today")
                    LegendItem(color = CalendarDotPending, label = "Pending")
                    LegendItem(color = CalendarDotDone, label = "Selected")
                    LegendItem(color = Color(0xFFFFD54F), label = "Reminder")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Selected Date Revisions
            if (uiState.selectedDate != null) {
                item {
                    Text(
                        text = "Revisions on ${formatLocalDate(uiState.selectedDate!!)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (uiState.selectedDateRevisions.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No upcoming events",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Events will appear here once you create them",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(uiState.selectedDateRevisions) { item ->
                        val revision = item.revision
                        val note = item.note
                        val nextRevision = item.nextRevision
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (note?.isReminder == true) Color(0xFFFFD54F)
                                            else if (revision.isCompleted) CalendarDotDone
                                            else CalendarDotPending
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = note?.title ?: "Unknown Note",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val statusText = if (revision.isCompleted) {
                                        "Completed"
                                    } else if (revision.scheduledAt > System.currentTimeMillis()) {
                                        "Upcoming"
                                    } else {
                                        "Pending"
                                    }
                                    Text(
                                        text = "Revision ${revision.revisionIndex + 1} • $statusText",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!revision.isCompleted && note != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (note.isReminder) {
                                        FilledTonalButton(
                                            onClick = { viewModel.deleteReminder(note) },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.error
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Delete now",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        FilledTonalButton(
                                            onClick = { viewModel.onReviseClick(revision, context, onNavigateToRevisionNote) },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = AccentGreen.copy(alpha = 0.15f),
                                                contentColor = AccentGreen
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Revise now",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    if (revision.isCompleted && nextRevision != null) {
                                        val nextDate = java.time.Instant.ofEpochMilli(nextRevision.scheduledAt)
                                            .atZone(java.time.ZoneId.systemDefault())
                                            .toLocalDate()
                                        Surface(
                                            onClick = { viewModel.selectDate(nextDate) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Next: ${formatDateTime(nextRevision.scheduledAt)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    } else {
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (revision.isCompleted) CalendarDotDone else CalendarDotPending
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthNavigator(
    currentMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTodayClick: () -> Unit,
    onAddNoteClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = "Previous month",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onNext) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Next month",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Today pill button
        Button(
            onClick = onTodayClick,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Add Note (+) button
        IconButton(
            onClick = onAddNoteClick,
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Note",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DayOfWeekHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        val daysOfWeek = listOf(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        )
        daysOfWeek.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    currentMonth: YearMonth,
    daysWithRevisions: Set<LocalDate>,
    monthData: Map<LocalDate, CalendarDayData>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    val firstDayOfMonth = currentMonth.atDay(1)
    val lastDayOfMonth = currentMonth.atEndOfMonth()
    val firstDayOfWeekIndex = firstDayOfMonth.dayOfWeek.value % 7
    val today = LocalDate.now()

    val totalCells = firstDayOfWeekIndex + lastDayOfMonth.dayOfMonth
    val rows = (totalCells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(7) { col ->
                    val dayIndex = row * 7 + col - firstDayOfWeekIndex + 1
                    val date = if (dayIndex in 1..lastDayOfMonth.dayOfMonth) {
                        currentMonth.atDay(dayIndex)
                    } else null

                    val dayData = date?.let { monthData[it] } ?: CalendarDayData()

                    CalendarDayCell(
                        date = date,
                        isToday = date == today,
                        dayData = dayData,
                        isSelected = date == selectedDate,
                        onClick = { date?.let(onDateSelected) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate?,
    isToday: Boolean,
    dayData: CalendarDayData,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (date == null) {
        Box(modifier = modifier.aspectRatio(0.8f))
        return
    }

    val cardBgColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val borderStroke = if (isSelected) {
        BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Card(
        modifier = modifier
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else if (isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.align(Alignment.TopStart)
            )

            if (dayData.pendingCount > 0) {
                val isPast = date != null && date.isBefore(LocalDate.now())
                val badgeColor = if (dayData.hasReminder) {
                    Color(0xFFFFD54F)
                } else if (isPast) {
                    Color(0xFFEF5350)
                } else {
                    Color(0xFF4CAF50)
                }
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(badgeColor, CircleShape)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayData.pendingCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (dayData.hasReminder) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${dayData.durationMinutes} min",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatLocalDate(date: LocalDate): String {
    val sdf = SimpleDateFormat("EEEE, MMM dd", Locale.getDefault())
    val calendar = java.util.Calendar.getInstance()
    calendar.set(date.year, date.monthValue - 1, date.dayOfMonth)
    return sdf.format(calendar.time)
}

private fun formatDateTime(epochMs: Long): String {
    val sdf = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
    return sdf.format(Date(epochMs))
}
