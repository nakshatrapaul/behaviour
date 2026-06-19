package com.behaviour.spacedrepetition.ui.screens.notes

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.roundToInt

@Composable
fun CustomCameraView(
    onClose: () -> Unit,
    onSaveImages: (List<File>) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()
    }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    val capturedImages = remember { mutableStateListOf<File>() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Viewfinder Preview
        CameraPreview(
            imageCapture = imageCapture,
            lensFacing = lensFacing,
            modifier = Modifier.fillMaxSize()
        )

        // Top controls layer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Camera",
                    tint = Color.White
                )
            }

            if (capturedImages.isNotEmpty()) {
                Button(
                    onClick = { onSaveImages(capturedImages) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save Images",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save to Note", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bottom Controls tray and shutter
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(bottom = 16.dp)
        ) {
            // Horizontal captured images preview tray
            if (capturedImages.isNotEmpty()) {
                ReorderablePhotoTray(
                    images = capturedImages,
                    onDelete = { file ->
                        capturedImages.remove(file)
                        try { file.delete() } catch (e: Exception) {}
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Capture Shutter and Switch lens buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Invisible spacer for centering Shutter
                Box(modifier = Modifier.size(48.dp))

                // Center Solid Circular Shutter
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            takePhoto(context, imageCapture) { file ->
                                capturedImages.add(file)
                            }
                        }
                )

                // Lens switch (Front/Back)
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Switch Camera Lens",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    imageCapture: ImageCapture,
    lensFacing: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    LaunchedEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onPhotoCaptured: (File) -> Unit
) {
    val photoFile = File.createTempFile(
        "camera_capture_",
        ".jpg",
        context.cacheDir
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onPhotoCaptured(photoFile)
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
            }
        }
    )
}

@Composable
fun ReorderablePhotoTray(
    images: SnapshotStateList<File>,
    onDelete: (File) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(images, key = { _, file -> file.absolutePath }) { index, file ->
            ReorderableItem(
                file = file,
                index = index,
                capturedImages = images,
                onDelete = onDelete
            )
        }
    }
}

@Composable
fun ReorderableItem(
    file: File,
    index: Int,
    capturedImages: SnapshotStateList<File>,
    onDelete: (File) -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val itemWidthPx = with(density) { 92.dp.toPx() }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .size(80.dp)
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .zIndex(if (isDragging) 10f else 1f)
            .pointerInput(file.absolutePath) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { 
                        isDragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        isDragging = false
                        offsetX = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        offsetX = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        
                        val threshold = itemWidthPx / 2
                        if (offsetX > threshold && index < capturedImages.lastIndex) {
                            val temp = capturedImages[index]
                            capturedImages[index] = capturedImages[index + 1]
                            capturedImages[index + 1] = temp
                            offsetX -= itemWidthPx
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (offsetX < -threshold && index > 0) {
                            val temp = capturedImages[index]
                            capturedImages[index] = capturedImages[index - 1]
                            capturedImages[index - 1] = temp
                            offsetX += itemWidthPx
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            PhotoThumbnail(file = file)
        }

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete(file)
            },
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.TopEnd)
                .offset(4.dp, (-4).dp)
                .background(Color.Red, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete image",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun PhotoThumbnail(file: File) {
    val bitmap = remember(file) { loadRotatedThumbnail(file) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Gray)
        )
    }
}

fun loadRotatedThumbnail(file: File): android.graphics.Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = android.graphics.Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        android.graphics.Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
