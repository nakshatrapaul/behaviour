package com.behaviour.spacedrepetition.ui.screens.notes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behaviour.spacedrepetition.ui.components.markdown.AttachmentHelper
import com.behaviour.spacedrepetition.ui.components.markdown.MarkdownRendererColumn
import com.behaviour.spacedrepetition.ui.theme.PrimaryDark
import com.behaviour.spacedrepetition.ui.theme.TextWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    onBack: () -> Unit,
    onNavigateToNote: (Long) -> Unit,
    viewModel: NoteDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Local state to manage cursor positions and selection
    var contentValue by remember { mutableStateOf(TextFieldValue("")) }
    var titleValue by remember { mutableStateOf("") }
    var showAttachmentSourceDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomCameraView by rememberSaveable { mutableStateOf(false) }
    
    // Sync DB state to local textfields once loaded
    var isInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading, uiState.noteExists) {
        if (!uiState.isLoading && !isInitialized) {
            contentValue = TextFieldValue(uiState.content)
            titleValue = uiState.title
            isInitialized = true
        }
    }

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
                viewModel.onContentChanged(newText)
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
                    viewModel.onContentChanged(newText)
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
        viewModel.onContentChanged(newText)
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
                viewModel.onContentChanged(newText)
            }
        )
    } else {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.noteExists) "Note Detail" else "New Note",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // Autosave on back
                        if (uiState.isEditMode) {
                            viewModel.saveNote()
                        }
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isEditMode) {
                        IconButton(
                            onClick = { viewModel.saveNote { onBack() } },
                            enabled = titleValue.isNotBlank() && !uiState.isLoading
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save Note", tint = PrimaryDark)
                        }
                    }
                    IconButton(onClick = { viewModel.toggleEditMode() }) {
                        Icon(
                            imageVector = if (uiState.isEditMode) Icons.Default.Visibility else Icons.Default.Edit,
                            contentDescription = if (uiState.isEditMode) "Preview Mode" else "Edit Mode"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
        ) {
            if (uiState.isLoading && !isInitialized) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (uiState.isEditMode) {
                        // Editor View
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                        ) {
                            OutlinedTextField(
                                value = titleValue,
                                onValueChange = {
                                    titleValue = it
                                    viewModel.onTitleChanged(it)
                                },
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
                                onValueChange = {
                                    contentValue = it
                                    viewModel.onContentChanged(it.text)
                                },
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
                                ToolbarButton(icon = Icons.AutoMirrored.Filled.FormatListBulleted, label = "Bullet") {
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
                    } else {
                        // Preview / Obsidian View Mode
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(
                                text = titleValue,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))

                            if (contentValue.text.isBlank()) {
                                Text(
                                    text = "This note is empty. Click edit icon in the top right to start writing.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                MarkdownRendererColumn(
                                    markdown = contentValue.text,
                                    onWikiLinkClick = { targetTitle ->
                                        viewModel.resolveWikiLink(targetTitle, onNavigateToNote)
                                    }
                                )
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

    if (uiState.showPremiumDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPremiumDialog() },
            title = {
                Text(
                    text = "Upgrade Required 👑",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You have reached the free tier limit of 5 study cards. Upgrade to Behave Premium to create unlimited notes, events, and unlock attachments!"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissPremiumDialog()
                        val checkoutUrl = com.behaviour.spacedrepetition.data.repository.BillingRepository.PADDLE_CHECKOUT_URL
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(checkoutUrl))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark)
                ) {
                    Text("Go Premium", color = TextWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPremiumDialog() }) {
                    Text("Maybe Later")
                }
            }
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
