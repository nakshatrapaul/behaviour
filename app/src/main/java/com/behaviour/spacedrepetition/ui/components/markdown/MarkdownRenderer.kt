package com.behaviour.spacedrepetition.ui.components.markdown

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.behaviour.spacedrepetition.ui.theme.PrimaryDark

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class ChecklistItem(val checked: Boolean, val text: String, val lineIndex: Int) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String = "") : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
    data class ImageEmbed(val fileName: String, val altText: String = "") : MarkdownBlock()
    data class PdfEmbed(val fileName: String) : MarkdownBlock()
}

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var inCodeBlock = false
    val codeBlockLines = mutableListOf<String>()
    var codeLanguage = ""

    for ((index, line) in lines.withIndex()) {
        val trimmed = line.trim()

        if (inCodeBlock) {
            if (trimmed.startsWith("```")) {
                inCodeBlock = false
                blocks.add(MarkdownBlock.CodeBlock(codeBlockLines.joinToString("\n"), codeLanguage))
                codeBlockLines.clear()
                codeLanguage = ""
            } else {
                codeBlockLines.add(line)
            }
            continue
        }

        if (trimmed.startsWith("```")) {
            inCodeBlock = true
            codeLanguage = trimmed.substring(3).trim()
            continue
        }

        when {
            trimmed == "---" || trimmed == "***" -> {
                blocks.add(MarkdownBlock.HorizontalRule)
            }
            trimmed.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Heading(1, trimmed.substring(2)))
            }
            trimmed.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Heading(2, trimmed.substring(3)))
            }
            trimmed.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Heading(3, trimmed.substring(4)))
            }
            trimmed.startsWith("#### ") -> {
                blocks.add(MarkdownBlock.Heading(4, trimmed.substring(5)))
            }
            trimmed.startsWith("- [ ] ") -> {
                blocks.add(MarkdownBlock.ChecklistItem(false, trimmed.substring(6), index))
            }
            trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> {
                blocks.add(MarkdownBlock.ChecklistItem(true, trimmed.substring(6), index))
            }
            trimmed.startsWith("- ") -> {
                blocks.add(MarkdownBlock.BulletItem(trimmed.substring(2)))
            }
            trimmed.startsWith("* ") -> {
                blocks.add(MarkdownBlock.BulletItem(trimmed.substring(2)))
            }
            trimmed.startsWith("![[") && trimmed.endsWith("]]") -> {
                val img = trimmed.removeSurrounding("![[", "]]")
                if (img.endsWith(".pdf", ignoreCase = true)) {
                    blocks.add(MarkdownBlock.PdfEmbed(img))
                } else {
                    blocks.add(MarkdownBlock.ImageEmbed(img))
                }
            }
            trimmed.startsWith("![") && trimmed.contains("](") && trimmed.endsWith(")") -> {
                val alt = trimmed.substringAfter("![").substringBefore("](")
                val url = trimmed.substringAfter("](").substringBefore(")")
                if (url.endsWith(".pdf", ignoreCase = true)) {
                    blocks.add(MarkdownBlock.PdfEmbed(url))
                } else {
                    blocks.add(MarkdownBlock.ImageEmbed(url, alt))
                }
            }
            else -> {
                if (trimmed.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Paragraph(line))
                }
            }
        }
    }
    if (inCodeBlock) {
        blocks.add(MarkdownBlock.CodeBlock(codeBlockLines.joinToString("\n"), codeLanguage))
    }
    return blocks
}

fun parseMarkdownInline(
    text: String,
    primaryColor: androidx.compose.ui.graphics.Color
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val pattern = """(\[\[[^\]]+\]\]|\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`|\[[^\]]+\]\([^)]+\))""".toRegex()
        val matches = pattern.findAll(text).toList()

        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > cursor) {
                append(text.substring(cursor, start))
            }

            val matchText = match.value
            when {
                matchText.startsWith("[[") && matchText.endsWith("]]") -> {
                    val content = matchText.removeSurrounding("[[", "]]")
                    val parts = content.split("|")
                    val target = parts[0].trim()
                    val display = if (parts.size > 1) parts[1].trim() else target

                    pushStringAnnotation(tag = "WIKILINK", annotation = target)
                    withStyle(style = SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)) {
                        append(display)
                    }
                    pop()
                }
                matchText.startsWith("**") && matchText.endsWith("**") -> {
                    val content = matchText.removeSurrounding("**")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(content)
                    }
                }
                matchText.startsWith("*") && matchText.endsWith("*") -> {
                    val content = matchText.removeSurrounding("*")
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                }
                matchText.startsWith("`") && matchText.endsWith("`") -> {
                    val content = matchText.removeSurrounding("`")
                    withStyle(style = SpanStyle(fontFamily = FontFamily.Monospace, background = primaryColor.copy(alpha = 0.1f))) {
                        append(" $content ")
                    }
                }
                matchText.startsWith("[") && matchText.contains("](") -> {
                    val label = matchText.substringAfter("[").substringBefore("](")
                    val url = matchText.substringAfter("](").substringBefore(")")
                    pushStringAnnotation(tag = "URL", annotation = url)
                    withStyle(style = SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline)) {
                        append(label)
                    }
                    pop()
                }
            }
            cursor = end
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

@Composable
fun MarkdownRenderer(
    markdown: String,
    onWikiLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState? = null,
    headerContent: @Composable (() -> Unit)? = null,
    footerContent: @Composable (() -> Unit)? = null
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val listState = lazyListState ?: remember { LazyListState() }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Optional header (title, progress bar, etc.)
        if (headerContent != null) {
            item(key = "__header__") {
                headerContent()
            }
        }

        items(
            count = blocks.size,
            key = { index -> "block_$index" }
        ) { index ->
            val block = blocks[index]
            when (block) {
                is MarkdownBlock.Heading -> {
                    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                        Text(
                            text = block.text,
                            style = when (block.level) {
                                1 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                                2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            },
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (block.level <= 2) {
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 1.dp
                            )
                        }
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    val annotatedString = remember(block.text) { parseMarkdownInline(block.text, primaryColor) }
                    ClickableText(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                        onClick = { offset ->
                            annotatedString.getStringAnnotations(tag = "WIKILINK", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    onWikiLinkClick(annotation.item)
                                }
                        }
                    )
                }
                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val annotatedString = remember(block.text) { parseMarkdownInline(block.text, primaryColor) }
                        ClickableText(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                            onClick = { offset ->
                                annotatedString.getStringAnnotations(tag = "WIKILINK", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        onWikiLinkClick(annotation.item)
                                    }
                            }
                        )
                    }
                }
                is MarkdownBlock.ChecklistItem -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = block.checked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = PrimaryDark,
                                uncheckedColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.scale(0.85f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val annotatedString = remember(block.text) { parseMarkdownInline(block.text, primaryColor) }
                        ClickableText(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (block.checked) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onBackground,
                                textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            onClick = { offset ->
                                annotatedString.getStringAnnotations(tag = "WIKILINK", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        onWikiLinkClick(annotation.item)
                                    }
                            }
                        )
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (block.language.isNotEmpty()) {
                                Text(
                                    text = block.language.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            Text(
                                text = block.code,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp
                    )
                }
                is MarkdownBlock.ImageEmbed -> {
                    AsyncImageLoader(
                        fileName = block.fileName,
                        altText = block.altText
                    )
                }
                is MarkdownBlock.PdfEmbed -> {
                    PdfRendererBlock(fileName = block.fileName)
                }
            }
        }

        // Optional footer (scroll hint, spacing)
        if (footerContent != null) {
            item(key = "__footer__") {
                footerContent()
            }
        }
    }
}

/**
 * Column-based (non-lazy) MarkdownRenderer for embedding inside already-scrollable
 * parents like LazyColumn items or verticalScroll Columns.
 * Use MarkdownRenderer() (LazyColumn-based) when the renderer IS the scroll root.
 */
@Composable
fun MarkdownRendererColumn(
    markdown: String,
    onWikiLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                        Text(
                            text = block.text,
                            style = when (block.level) {
                                1 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                                2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            },
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (block.level <= 2) {
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 1.dp
                            )
                        }
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    val annotatedString = remember(block.text) { parseMarkdownInline(block.text, primaryColor) }
                    ClickableText(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                        onClick = { offset ->
                            annotatedString.getStringAnnotations(tag = "WIKILINK", start = offset, end = offset)
                                .firstOrNull()?.let { annotation ->
                                    onWikiLinkClick(annotation.item)
                                }
                        }
                    )
                }
                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val annotatedString = remember(block.text) { parseMarkdownInline(block.text, primaryColor) }
                        ClickableText(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                            onClick = { offset ->
                                annotatedString.getStringAnnotations(tag = "WIKILINK", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        onWikiLinkClick(annotation.item)
                                    }
                            }
                        )
                    }
                }
                is MarkdownBlock.ChecklistItem -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = block.checked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = PrimaryDark,
                                uncheckedColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.scale(0.85f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val annotatedString = remember(block.text) { parseMarkdownInline(block.text, primaryColor) }
                        ClickableText(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (block.checked) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onBackground,
                                textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            onClick = { offset ->
                                annotatedString.getStringAnnotations(tag = "WIKILINK", start = offset, end = offset)
                                    .firstOrNull()?.let { annotation ->
                                        onWikiLinkClick(annotation.item)
                                    }
                            }
                        )
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (block.language.isNotEmpty()) {
                                Text(
                                    text = block.language.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            Text(
                                text = block.code,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp
                    )
                }
                is MarkdownBlock.ImageEmbed -> {
                    AsyncImageLoader(
                        fileName = block.fileName,
                        altText = block.altText
                    )
                }
                is MarkdownBlock.PdfEmbed -> {
                    PdfRendererBlock(fileName = block.fileName)
                }
            }
        }
    }
}

suspend fun loadDownsampledBitmap(filePath: String, targetWidth: Int = 600): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)
            
            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return@withContext null
            
            var inSampleSize = 1
            if (srcWidth > targetWidth || srcHeight > targetWidth) {
                val halfWidth = srcWidth / 2
                val halfHeight = srcHeight / 2
                while (halfWidth / inSampleSize >= targetWidth || halfHeight / inSampleSize >= targetWidth) {
                    inSampleSize *= 2
                }
            }
            
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val decodedBitmap = BitmapFactory.decodeFile(filePath, decodeOptions) ?: return@withContext null
            
            // Correct rotation using EXIF data
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotatedBitmap = Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true)
                if (rotatedBitmap != decodedBitmap) {
                    decodedBitmap.recycle()
                }
                rotatedBitmap
            } else {
                decodedBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

object BitmapCache {
    private val cache = object : android.util.LruCache<String, Bitmap>(32 * 1024 * 1024) { // 32MB cache
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

@Composable
fun AsyncImageLoader(
    fileName: String,
    altText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val file = remember(fileName) {
        if (fileName.startsWith("http") || fileName.startsWith("content://") || fileName.startsWith("file://")) {
            null
        } else {
            AttachmentHelper.getAttachmentFile(context, fileName)
        }
    }

    if (file != null && file.exists()) {
        val cachedBitmap = remember(file) { BitmapCache.get(file.absolutePath) }
        var bitmap by remember(file) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(cachedBitmap?.asImageBitmap()) }
        var isLoading by remember(file) { mutableStateOf(cachedBitmap == null) }
        var loadError by remember(file) { mutableStateOf(false) }

        if (cachedBitmap == null) {
            LaunchedEffect(file) {
                withContext(Dispatchers.IO) {
                    try {
                        val bmp = loadDownsampledBitmap(file.absolutePath, 600)
                        if (bmp != null) {
                            BitmapCache.put(file.absolutePath, bmp)
                            bitmap = bmp.asImageBitmap()
                            isLoading = false
                        } else {
                            loadError = true
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        loadError = true
                        isLoading = false
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else if (loadError || bitmap == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Failed to load image: $fileName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = altText.ifEmpty { "Note Image" },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Image not found: $fileName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PdfRendererBlock(
    fileName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val file = remember(fileName) { AttachmentHelper.getAttachmentFile(context, fileName) }
    
    if (file.exists()) {
        // Store ImageBitmap wrappers so we don't re-create them on every recomposition
        var imageBitmaps by remember(file) { mutableStateOf<List<ImageBitmap>>(emptyList()) }
        var isLoading by remember(file) { mutableStateOf(true) }
        var loadError by remember(file) { mutableStateOf(false) }

        LaunchedEffect(file) {
            withContext(Dispatchers.IO) {
                try {
                    val list = mutableListOf<ImageBitmap>()
                    val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val pdfRenderer = PdfRenderer(parcelFileDescriptor)
                    val pageCount = pdfRenderer.pageCount
                    for (i in 0 until pageCount) {
                        val key = "${file.absolutePath}_page_$i"
                        var cached = BitmapCache.get(key)
                        if (cached == null) {
                            val page = pdfRenderer.openPage(i)
                            val width = 720
                            val height = (page.height.toFloat() / page.width.toFloat() * width).toInt()
                            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            BitmapCache.put(key, bitmap)
                            cached = bitmap
                            page.close()
                        }
                        list.add(cached.asImageBitmap())
                    }
                    pdfRenderer.close()
                    parcelFileDescriptor.close()
                    imageBitmaps = list
                    isLoading = false
                } catch (e: Exception) {
                    e.printStackTrace()
                    loadError = true
                    isLoading = false
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Rendering PDF pages...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (loadError || imageBitmaps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Failed to load PDF: $fileName",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                imageBitmaps.forEachIndexed { pageIndex, imgBitmap ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Image(
                            bitmap = imgBitmap,
                            contentDescription = "Page ${pageIndex + 1} of $fileName",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PDF not found: $fileName",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

