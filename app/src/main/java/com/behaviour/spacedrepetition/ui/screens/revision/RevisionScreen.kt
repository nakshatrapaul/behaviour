package com.behaviour.spacedrepetition.ui.screens.revision

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behaviour.spacedrepetition.ui.theme.*
import com.behaviour.spacedrepetition.ui.components.markdown.MarkdownRendererColumn
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RevisionScreen(
    onNavigateToRevisionNote: (Long) -> Unit = {},
    viewModel: RevisionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.completedMessage) {
        if (uiState.completedMessage != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearCompletedMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Revisions",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${uiState.pendingItems.size} study revisions scheduled",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryDark)
                }
            } else if (uiState.pendingItems.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🎉",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "All caught up!",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No pending revisions right now",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.pendingItems, key = { item -> item.revision.id }) { item ->
                        RevisionCard(
                            item = item,
                            onComplete = { onNavigateToRevisionNote(item.revision.id) },
                            onDeleteReminder = { viewModel.deleteReminder(item.note) }
                        )
                    }
                }
            }
        }

        // Completion snackbar
        AnimatedVisibility(
            visible = uiState.completedMessage != null,
            enter = slideInVertically(tween(300)) { it } + fadeIn(),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = AccentGreen
                ),
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                Text(
                    text = uiState.completedMessage ?: "",
                    color = TextWhite,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun RevisionCard(
    item: RevisionItem,
    onComplete: () -> Unit,
    onDeleteReminder: () -> Unit
) {
    val totalRevisions = item.note.totalRevisions
    val progress = (item.revision.revisionIndex + 1).toFloat() / totalRevisions

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.note.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (item.note.isReminder) "Reminder" else "Revision ${item.revision.revisionIndex + 1} of $totalRevisions",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.note.isReminder) Color(0xFFFFD54F) else PrimaryDark
                        )
                        if (item.revision.scheduledAt > System.currentTimeMillis()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Future", style = MaterialTheme.typography.labelSmall) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    labelColor = MaterialTheme.colorScheme.primary
                                ),
                                border = null,
                                modifier = Modifier.height(20.dp)
                            )
                        }
                    }
                }

                if (item.note.isReminder) {
                    FilledTonalButton(
                        onClick = onDeleteReminder,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete now", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onComplete,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AccentGreen.copy(alpha = 0.15f),
                            contentColor = AccentGreen
                        )
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Revise now", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content preview
            MarkdownRendererColumn(
                markdown = item.note.content,
                onWikiLinkClick = {}
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = PrimaryDark,
                    trackColor = PrimaryDark.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatRevisionDate(item.revision.scheduledAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatRevisionDate(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = epochMs - now

    return if (diff > 0) {
        when {
            diff < 60 * 60 * 1000 -> "In ${diff / (60 * 1000)}m"
            diff < 24 * 60 * 60 * 1000 -> "In ${diff / (60 * 60 * 1000)}h"
            diff < 7 * 24 * 60 * 60 * 1000 -> "In ${diff / (24 * 60 * 60 * 1000)}d"
            else -> {
                val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                "Due ${sdf.format(Date(epochMs))}"
            }
        }
    } else {
        val absDiff = -diff
        when {
            absDiff < 60 * 60 * 1000 -> "Due now"
            absDiff < 24 * 60 * 60 * 1000 -> "${absDiff / (60 * 60 * 1000)}h ago"
            absDiff < 7 * 24 * 60 * 60 * 1000 -> "${absDiff / (24 * 60 * 60 * 1000)}d ago"
            else -> {
                val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                "Due ${sdf.format(Date(epochMs))}"
            }
        }
    }
}
