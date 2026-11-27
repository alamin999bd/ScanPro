package com.scanpro.pdfdocscanner.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.core.FileStore
import com.scanpro.pdfdocscanner.ui.components.EmptyState
import com.scanpro.pdfdocscanner.ui.components.ProgressDialog
import com.scanpro.pdfdocscanner.ui.components.ScanProTopBar
import com.scanpro.pdfdocscanner.ui.components.TextInputDialog
import com.scanpro.pdfdocscanner.ui.components.containerViewModel
import com.scanpro.pdfdocscanner.ui.editor.ID_CARD_ASPECT
import com.scanpro.pdfdocscanner.ui.editor.PageEditorScreen
import java.io.File

@Composable
fun ScanScreen(
    container: AppContainer,
    isIdMode: Boolean,
    onBack: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenViewer: (Long) -> Unit,
) {
    val context = LocalContext.current
    val vm = containerViewModel(container, key = if (isIdMode) "scan_id" else "scan_doc") {
        ScanViewModel(it.documents, it.fileStore, it.pdfEngine, it.ocrEngine, it.settings, isIdMode)
    }
    val ui by vm.ui.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var askedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        askedOnce = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        vm.addFromGallery(uris)
    }
    val pickFromGallery = {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    var showNameDialog by remember { mutableStateOf(false) }
    val title = if (isIdMode) "ID Scan" else "Document Scan"

    // Page editor takes over the whole screen.
    val editingIndex = ui.editing
    val editingFile = editingIndex?.let { ui.pages.getOrNull(it) }
    if (editingIndex != null && editingFile != null) {
        PageEditorScreen(
            container = container,
            file = editingFile,
            initialAspect = if (isIdMode) ID_CARD_ASPECT else null,
            onCancel = vm::closeEditor,
            onApplied = { vm.replacePage(editingIndex, it) },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScanProTopBar(title = title, onBack = onBack) },
    ) { padding ->
        val saved = ui.savedName
        when {
            saved != null -> SavedView(
                name = saved,
                modifier = Modifier.padding(padding),
                onView = { ui.savedId?.let(onOpenViewer) },
                onScanMore = vm::startOver,
                onOpenDocuments = onOpenDocuments,
            )
            !hasPermission -> EmptyState(
                icon = Icons.Rounded.DocumentScanner,
                title = "Camera access needed",
                message = "ScanPro uses the camera only to capture your pages. Photos are processed on this device and never uploaded. " +
                    "No camera? Use \"Image to PDF\" on the home screen to build a document from gallery photos.",
                modifier = Modifier.padding(padding),
                actionLabel = if (askedOnce) "Open app settings" else "Allow camera",
                onAction = {
                    if (askedOnce) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
            )
            else -> CaptureContent(
                modifier = Modifier.padding(padding),
                isIdMode = isIdMode,
                pages = ui.pages,
                error = ui.error,
                newFile = vm::newCaptureFile,
                onCaptured = vm::onCaptured,
                onFailed = vm::onCaptureFailed,
                onEdit = vm::openEditor,
                onRemove = vm::removePage,
                onMove = vm::movePage,
                onGallery = pickFromGallery,
                onSave = { showNameDialog = true },
            )
        }
    }

    if (showNameDialog) {
        TextInputDialog(
            title = "Save as PDF",
            initialValue = FileStore.defaultScanName(if (isIdMode) "ID" else "Scan"),
            label = "File name",
            confirmLabel = "Save",
            onConfirm = { vm.save(it); showNameDialog = false },
            onDismiss = { showNameDialog = false },
        )
    }
    if (ui.saving) ProgressDialog("Creating PDF…")
}

@Composable
private fun CaptureContent(
    modifier: Modifier,
    isIdMode: Boolean,
    pages: List<File>,
    error: String?,
    newFile: () -> File,
    onCaptured: (File) -> Unit,
    onFailed: (File, String) -> Unit,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onGallery: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    val shutterSource = remember { MutableInteractionSource() }

    val maxPages = if (isIdMode) 2 else Int.MAX_VALUE
    val canCapture = pages.size < maxPages && !capturing
    val instruction = when {
        !isIdMode -> if (pages.isEmpty()) "Place the page flat and capture" else "Capture the next page or tap Save PDF"
        pages.isEmpty() -> "Fit the FRONT of the ID inside the frame"
        pages.size == 1 -> "Now the BACK of the ID (or save with front only)"
        else -> "Front and back captured"
    }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner) {
        var disposed = false
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            camera = runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }.getOrNull()
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            camera = null
            runCatching {
                val f = ProcessCameraProvider.getInstance(context)
                if (f.isDone) f.get().unbindAll()
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(MaterialTheme.shapes.large)
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera) {
                        detectTapGestures { offset ->
                            val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        }
                    },
            )

            if (isIdMode) IdGuideFrame(Modifier.fillMaxSize())

            Text(
                instruction,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )

            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                IconButton(
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                        imageCapture.flashMode = flashMode
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                ) {
                    Icon(
                        when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto
                            ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                            else -> Icons.Rounded.FlashOff
                        },
                        contentDescription = "Flash",
                        tint = Color.White,
                    )
                }
            }
        }

        if (pages.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(pages, key = { _, f -> f.name }) { index, file ->
                    PageThumb(
                        file = file,
                        index = index,
                        canMoveLeft = index > 0,
                        canMoveRight = index < pages.lastIndex,
                        onEdit = { onEdit(index) },
                        onRemove = { onRemove(index) },
                        onMove = { delta -> onMove(index, delta) },
                    )
                }
            }
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onGallery, enabled = pages.size < maxPages) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Add from gallery")
                }
                Text(
                    if (pages.isEmpty()) "No pages" else "${pages.size}",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            val ringColor = if (canCapture) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(4.dp, ringColor, CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(ringColor)
                    .clickable(
                        interactionSource = shutterSource,
                        indication = null,
                        enabled = canCapture,
                    ) {
                        capturing = true
                        val file = newFile()
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture.takePicture(
                            options,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    capturing = false
                                    onCaptured(file)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    capturing = false
                                    onFailed(file, "Capture failed. Please try again.")
                                }
                            },
                        )
                    },
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Button(onClick = onSave, enabled = pages.isNotEmpty()) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Save PDF")
                }
            }
        }
    }
}

/** Card-shaped guide (ID-1 ratio) drawn over the preview in ID mode. */
@Composable
private fun IdGuideFrame(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width * 0.86f
        val h = w / ID_CARD_ASPECT
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val dim = Color.Black.copy(alpha = 0.45f)
        drawRect(dim, Offset(0f, 0f), Size(size.width, top))
        drawRect(dim, Offset(0f, top + h), Size(size.width, size.height - top - h))
        drawRect(dim, Offset(0f, top), Size(left, h))
        drawRect(dim, Offset(left + w, top), Size(size.width - left - w, h))
        drawRoundRect(
            Color.White,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 4f),
        )
    }
}

@Composable
private fun PageThumb(
    file: File,
    index: Int,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            AsyncImage(
                model = file,
                contentDescription = "Page ${index + 1}. Tap to edit",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 64.dp, height = 84.dp)
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable(onClick = onEdit),
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
            ) {
                Icon(Icons.Rounded.Close, "Remove page ${index + 1}", tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Text(
                "${index + 1}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 6.dp),
            )
        }
        Row {
            IconButton(onClick = { onMove(-1) }, enabled = canMoveLeft, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.ChevronLeft, "Move page ${index + 1} earlier", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveRight, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.ChevronRight, "Move page ${index + 1} later", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SavedView(
    name: String,
    modifier: Modifier,
    onView: () -> Unit,
    onScanMore: () -> Unit,
    onOpenDocuments: () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Saved", style = MaterialTheme.typography.titleLarge)
            Text(
                "\"$name\" is in My Documents.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onView, modifier = Modifier.fillMaxWidth()) { Text("View PDF") }
            OutlinedButton(onClick = onOpenDocuments, modifier = Modifier.fillMaxWidth()) { Text("Open My Documents") }
            OutlinedButton(onClick = onScanMore, modifier = Modifier.fillMaxWidth()) { Text("Scan another") }
        }
    }
}
