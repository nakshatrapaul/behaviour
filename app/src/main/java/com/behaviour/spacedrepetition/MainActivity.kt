package com.behaviour.spacedrepetition

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.behaviour.spacedrepetition.data.repository.NoteRepository
import com.behaviour.spacedrepetition.ui.navigation.AppNavigation
import com.behaviour.spacedrepetition.ui.theme.BehaviourTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var noteRepository: NoteRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            noteRepository.alignNotesTo400Revisions()

            // Automatically schedule the next pending revision alarm on app launch
            val nextRevision = noteRepository.getNextPendingRevision()
            if (nextRevision != null) {
                val note = noteRepository.getNoteById(nextRevision.noteId)
                if (note != null) {
                    val alarmScheduler = com.behaviour.spacedrepetition.scheduling.AlarmScheduler()
                    val triggerAt = if (nextRevision.scheduledAt <= System.currentTimeMillis()) {
                        System.currentTimeMillis() + 10_000 // Trigger in 10 seconds if already overdue
                    } else {
                        nextRevision.scheduledAt
                    }
                    alarmScheduler.scheduleRevisionAlarm(
                        context = applicationContext,
                        revisionId = nextRevision.id,
                        noteTitle = note.title,
                        triggerAtMillis = triggerAt
                    )
                }
            }
        }

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()

        setContent {
            BehaviourTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val pendingCount by noteRepository
                        .getPendingRevisionCount()
                        .collectAsState(initial = 0)

                    AppNavigation(
                        pendingRevisionCount = pendingCount
                    )
                }
            }
        }
    }
}
