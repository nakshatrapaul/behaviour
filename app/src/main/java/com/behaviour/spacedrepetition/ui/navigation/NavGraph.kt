package com.behaviour.spacedrepetition.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.behaviour.spacedrepetition.ui.components.BottomNavBar
import com.behaviour.spacedrepetition.ui.screens.auth.AuthScreen
import com.behaviour.spacedrepetition.ui.screens.calendar.CalendarScreen
import com.behaviour.spacedrepetition.ui.screens.home.HomeScreen
import com.behaviour.spacedrepetition.ui.screens.notes.NotesScreen
import com.behaviour.spacedrepetition.ui.screens.notes.NoteDetailScreen
import com.behaviour.spacedrepetition.ui.screens.revision.RevisionScreen
import com.behaviour.spacedrepetition.ui.screens.revision.RevisionNoteScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument

object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val NOTES = "notes"
    const val NOTE_DETAIL = "note_detail/{noteId}"
    const val REVISION = "revision"
    const val REVISION_NOTE = "revision_note/{revisionId}"
    const val CALENDAR = "calendar"
}

@Composable
fun AppNavigation(
    startDestination: String = Routes.AUTH,
    pendingRevisionCount: Int = 0
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.AUTH

    val showBottomBar = currentRoute in listOf(
        Routes.HOME, Routes.NOTES, Routes.REVISION, Routes.CALENDAR
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (route != currentRoute) {
                            navController.navigate(route) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    pendingCount = pendingRevisionCount
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(
                bottom = if (showBottomBar) paddingValues.calculateBottomPadding() else 0.dp
            ),
            enterTransition = {
                fadeIn(animationSpec = tween(200))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200))
            }
        ) {
            composable(Routes.AUTH) {
                AuthScreen(
                    onAuthSuccess = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onNavigateToNotes = {
                        navController.navigate(Routes.NOTES) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToRevision = {
                        navController.navigate(Routes.CALENDAR) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToAddNote = {
                        navController.navigate("note_detail/0")
                    },
                    onNoteClick = { noteId ->
                        navController.navigate("note_detail/$noteId")
                    }
                )
            }

            composable(Routes.NOTES) {
                NotesScreen(
                    onNoteClick = { noteId ->
                        navController.navigate("note_detail/$noteId")
                    }
                )
            }

            composable(Routes.NOTE_DETAIL) {
                NoteDetailScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToNote = { id ->
                        navController.navigate("note_detail/$id")
                    }
                )
            }

            composable(Routes.REVISION) {
                RevisionScreen(
                    onNavigateToRevisionNote = { revisionId ->
                        navController.navigate("revision_note/$revisionId")
                    }
                )
            }

            composable(
                Routes.REVISION_NOTE,
                arguments = listOf(navArgument("revisionId") { type = NavType.LongType })
            ) {
                RevisionNoteScreen(
                    revisionId = it.arguments?.getLong("revisionId") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.CALENDAR) {
                CalendarScreen(
                    onAddNoteClick = {
                        navController.navigate("note_detail/0")
                    },
                    onNavigateToRevisionNote = { revisionId ->
                        navController.navigate("revision_note/$revisionId")
                    }
                )
            }
        }
    }
}
