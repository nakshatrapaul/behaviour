package com.behaviour.spacedrepetition.ui.screens.revision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behaviour.spacedrepetition.ui.components.markdown.AttachmentHelper
import com.behaviour.spacedrepetition.ui.components.markdown.MarkdownRenderer
import com.behaviour.spacedrepetition.ui.screens.notes.CustomCameraView
import com.behaviour.spacedrepetition.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisionNoteScreen(
    revisionId: Long,
    onBack: () -> Unit,
    viewModel: RevisionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()

    // Find the revision item matching the given revisionId
    val revisionItem = uiState.pendingItems.find { it.revision.id == revisionId }

    // Local state to manage edit mode, cursor positions, and selection
    var isEditMode by remember { mutableStateOf(false) }
    var contentValue by remember { mutableStateOf(TextFieldValue("")) }
    var titleValue by remember { mutableStateOf("") }

    // Sync DB state to local state once loaded
    var isInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(revisionItem) {
        if (revisionItem != null && !isInitialized) {
            contentValue = TextFieldValue(revisionItem.note.content)
            titleValue = revisionItem.note.title
            isInitialized = true
        }
    }

    var showAttachmentSourceDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomCameraView by rememberSaveable { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                showCustomCameraView = true
            } else {
                Toast.makeText(
                    context,
                    "Camera permission is required to take photos.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    )

    val pickMultipleMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                var newText = contentValue.text
                var currentCursor = contentValue.selection.start
                uris.forEach { uri ->
                    val savedName = AttachmentHelper.saveImageFromUri(context, uri)
                    if (savedName != null) {
                        val tag = "\n![[$savedName]]\n"
                        newText = newText.substring(0, currentCursor) + tag + newText.substring(currentCursor)
                        currentCursor += tag.length
                    }
                }
                contentValue = TextFieldValue(newText, TextRange(currentCursor))
            }
        }
    )

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                val savedName = copyPdfFromUri(context, uri)
                if (savedName != null) {
                    val tag = "\n![[$savedName]]\n"
                    val selection = contentValue.selection
                    val text = contentValue.text
                    val newText = text.substring(0, selection.start) + tag + text.substring(selection.end)
                    val newCursor = selection.start + tag.length
                    contentValue = TextFieldValue(newText, TextRange(newCursor))
                }
            }
        }
    )

    fun insertFormat(prefix: String, suffix: String = "") {
        val selection = contentValue.selection
        val text = contentValue.text
        val selectedText = text.substring(selection.start, selection.end)
        val replacement = prefix + selectedText + suffix
        val newText = text.substring(0, selection.start) + replacement + text.substring(selection.end)
        val newCursor = selection.start + prefix.length + selectedText.length + suffix.length
        contentValue = TextFieldValue(newText, TextRange(newCursor))
    }

    // Show buttons when user scrolls near the end — use derivedStateOf to avoid
    // recomposing on every single scroll pixel.
    val showButtons by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) true
            else {
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleItem >= totalItems - 2
            }
        }
    }

    val buttonsAlpha by animateFloatAsState(
        targetValue = if (showButtons && !isEditMode) 1f else 0f,
        animationSpec = tween(400),
        label = "buttonsAlpha"
    )

    // Debounce guard to prevent double-tap on recall buttons
    var isProcessingRecall by remember { mutableStateOf(false) }

    if (revisionItem == null && !uiState.isLoading) {
        // Item already completed or not found — go back
        LaunchedEffect(Unit) { onBack() }
        return
    }

    if (showCustomCameraView) {
        CustomCameraView(
            onClose = { showCustomCameraView = false },
            onSaveImages = { files ->
                showCustomCameraView = false
                var newText = contentValue.text
                var currentCursor = contentValue.selection.start
                files.forEach { file ->
                    val savedName = copyCameraPhotoToAttachments(context, file)
                    if (savedName != null) {
                        val tag = "\n![[$savedName]]\n"
                        newText = newText.substring(0, currentCursor) + tag + newText.substring(currentCursor)
                        currentCursor += tag.length
                    }
                    try { file.delete() } catch (e: Exception) {}
                }
                contentValue = TextFieldValue(newText, TextRange(currentCursor))
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (isEditMode) "Edit Revision Note" else "Revision",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (revisionItem != null && !isEditMode) {
                                val totalRevisions = revisionItem.note.totalRevisions
                                Text(
                                    text = "Round ${revisionItem.revision.revisionIndex + 1} of $totalRevisions",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryDark
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isEditMode) {
                                // Cancel edit and discard changes (reload from revisionItem)
                                if (revisionItem != null) {
                                    titleValue = revisionItem.note.title
                                    contentValue = TextFieldValue(revisionItem.note.content)
                                }
                                isEditMode = false
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        if (revisionItem != null) {
                            if (isEditMode) {
                                // Save button
                                IconButton(
                                    onClick = {
                                        viewModel.updateNoteContent(
                                            note = revisionItem.note,
                                            newTitle = titleValue,
                                            newContent = contentValue.text
                                        )
                                        isEditMode = false
                                    },
                                    enabled = titleValue.isNotBlank()
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Save Note", tint = PrimaryDark)
                                }
                            } else {
                                // Edit button
                                IconButton(onClick = { isEditMode = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Note")
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { paddingValues ->
            if (revisionItem == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryDark)
                }
                return@Scaffold
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .imePadding()
            ) {
                if (isEditMode) {
                    // Editor Layout
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        ) {
                            OutlinedTextField(
                                value = titleValue,
                                onValueChange = { titleValue = it },
                                placeholder = { Text("Title", style = MaterialTheme.typography.titleLarge) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = contentValue,
                                onValueChange = { contentValue = it },
                                placeholder = { Text("Start typing markdown... Use # for headings, ** for bold...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            )
                        }

                        // Formatting Shortcut Toolbar
                        Surface(
                            tonalElevation = 3.dp,
                            modifier = Modifier.fillMaxWidth(),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ToolbarButton(icon = Icons.Default.PhotoLibrary, label = "Gallery") {
                                    pickMultipleMediaLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                ToolbarButton(icon = Icons.Default.PhotoCamera, label = "Camera") {
                                    val permissionCheck = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    )
                                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                        showCustomCameraView = true
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                                ToolbarButton(icon = Icons.Default.PictureAsPdf, label = "PDF") {
                                    pdfPickerLauncher.launch("application/pdf")
                                }
                                ToolbarButton(icon = Icons.Default.FormatBold, label = "Bold") {
                                    insertFormat("**", "**")
                                }
                                ToolbarButton(icon = Icons.Default.FormatItalic, label = "Italic") {
                                    insertFormat("*", "*")
                                }
                                ToolbarButton(icon = Icons.Default.Title, label = "H1") {
                                    insertFormat("\n# ", "\n")
                                }
                                ToolbarButton(icon = Icons.Default.Title, label = "H2") {
                                    insertFormat("\n## ", "\n")
                                }
                                ToolbarButton(icon = Icons.Default.FormatListBulleted, label = "Bullet") {
                                    insertFormat("\n- ", "\n")
                                }
                                ToolbarButton(icon = Icons.Default.CheckBox, label = "Checklist") {
                                    insertFormat("\n- [ ] ", "\n")
                                }
                                ToolbarButton(icon = Icons.Default.Link, label = "WikiLink") {
                                    insertFormat("[[", "]]")
                                }
                            }
                        }
                    }
                } else {
                    // Viewer Layout — MarkdownRenderer is now a LazyColumn internally,
                    // so we get virtualized scrolling for images/PDFs.
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        MarkdownRenderer(
                            markdown = contentValue.text,
                            onWikiLinkClick = {},
                            lazyListState = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            headerContent = {
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Note title
                                    Text(
                                        text = titleValue,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Progress indicator
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val totalRevisions = revisionItem.note.totalRevisions
                                        val progress = (revisionItem.revision.revisionIndex + 1).toFloat() / totalRevisions
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = PrimaryDark,
                                            trackColor = PrimaryDark.copy(alpha = 0.15f)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Icon(
                                            Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Revision ${revisionItem.revision.revisionIndex + 1}/$totalRevisions",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            },
                            footerContent = {
                                Column {
                                    Spacer(modifier = Modifier.height(32.dp))

                                    // Scroll hint (fades out as user scrolls)
                                    if (!showButtons) {
                                        Text(
                                            text = "↓ Scroll down to mark revision",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(160.dp)) // space for bottom buttons
                                }
                            }
                        )

                        // Bottom gradient overlay + recall buttons
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .alpha(buttonsAlpha)
                        ) {
                            // Gradient fade
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                                MaterialTheme.colorScheme.background
                                            )
                                        )
                                    )
                            )

                            // Button area
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .padding(bottom = 32.dp, top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Didn't recall button
                                    OutlinedButton(
                                        onClick = {
                                            if (!isProcessingRecall) {
                                                isProcessingRecall = true
                                                viewModel.completeRevision(revisionItem, recalled = false)
                                                onBack()
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                            brush = Brush.horizontalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                                )
                                            )
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Didn't Recall",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1
                                        )
                                    }

                                    // Recalled properly button
                                    Button(
                                        onClick = {
                                            if (!isProcessingRecall) {
                                                isProcessingRecall = true
                                                viewModel.completeRevision(revisionItem, recalled = true)
                                                onBack()
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentGreen,
                                            contentColor = TextWhite
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Recalled",
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAttachmentSourceDialog) {
            AlertDialog(
                onDismissRequest = { showAttachmentSourceDialog = false },
                title = {
                    Text(
                        text = "Add Attachment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Choose an option to add files to your note.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Button(
                            onClick = {
                                showAttachmentSourceDialog = false
                                val permissionCheck = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                )
                                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                    showCustomCameraView = true
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Camera (Take Photos)")
                        }
                        
                        Button(
                            onClick = {
                                showAttachmentSourceDialog = false
                                pickMultipleMediaLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gallery (Select Photos)")
                        }

                        Button(
                            onClick = {
                                showAttachmentSourceDialog = false
                                pdfPickerLauncher.launch("application/pdf")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PDF (Insert Document)")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = { showAttachmentSourceDialog = false }
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.outline)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(44.dp)
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun copyCameraPhotoToAttachments(context: Context, tempFile: File): String? {
    return try {
        val extension = tempFile.extension.ifEmpty { "jpg" }
        val fileName = "img_${System.currentTimeMillis()}_${(100..999).random()}.$extension"
        val attachmentsDir = File(context.filesDir, "attachments")
        if (!attachmentsDir.exists()) {
            attachmentsDir.mkdirs()
        }
        val destFile = File(attachmentsDir, fileName)
        tempFile.inputStream().use { inputStream ->
            destFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        fileName
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun copyPdfFromUri(context: Context, uri: Uri): String? {
    return try {
        val contentResolver = context.contentResolver
        val fileName = "doc_${System.currentTimeMillis()}.pdf"
        val attachmentsDir = File(context.filesDir, "attachments")
        if (!attachmentsDir.exists()) {
            attachmentsDir.mkdirs()
        }
        val destFile = File(attachmentsDir, fileName)
        contentResolver.openInputStream(uri)?.use { inputStream ->
            destFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        fileName
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
