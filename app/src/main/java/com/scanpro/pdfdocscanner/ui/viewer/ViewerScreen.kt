package com.scanpro.pdfdocscanner.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.data.db.DocumentEntity
import com.scanpro.pdfdocscanner.ui.components.ScanProTopBar
import com.scanpro.pdfdocscanner.ui.components.containerViewModel
import com.scanpro.pdfdocscanner.ui.components.rememberDocExporter
import com.scanpro.pdfdocscanner.ui.components.shareDocument
import kotlin.math.min

private val ZOOM_LEVELS = listOf(1f, 1.5f, 2f, 3f)

/** In-app preview for PDFs (rendered offline with PdfRenderer) and image documents. */
@Composable
fun ViewerScreen(container: AppContainer, docId: Long, onBack: () -> Unit) {
    val vm = containerViewModel(container, key = "viewer_$docId") { ViewerViewModel(it.documents, docId, it.fileStore, it.pdfEngine) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val export = rememberDocExporter(container)
    var zoomIndex by remember { mutableIntStateOf(0) }

    val doc: DocumentEntity? = when (val s = state) {
        is ViewerState.Pdf -> s.doc
        is ViewerState.Picture -> s.doc
        else -> null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScanProTopBar(
                title = doc?.name ?: "Preview",
                onBack = onBack,
                actions = {
                    if (doc != null) {
                        IconButton(onClick = { zoomIndex = (zoomIndex + 1) % ZOOM_LEVELS.size }) {
                            Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom ${(ZOOM_LEVELS[zoomIndex] * 100).toInt()}%")
                        }
                        IconButton(onClick = { context.shareDocument(container, doc) }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { export(doc) }) {
                            Icon(Icons.Rounded.SaveAlt, contentDescription = "Save to device")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                ViewerState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ViewerState.Failed -> Text(
                    s.message,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is ViewerState.NeedsPassword -> PasswordPrompt(s.wrongPassword, vm::submitPassword, Modifier.align(Alignment.Center))
                is ViewerState.Pdf -> PdfPages(vm, s, ZOOM_LEVELS[zoomIndex])
                is ViewerState.Picture -> PictureView(container, s.doc, ZOOM_LEVELS[zoomIndex])
            }
        }
    }
}

@Composable
private fun PasswordPrompt(wrong: Boolean, onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    var password by remember { mutableStateOf("") }
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("This PDF is password-protected.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            isError = wrong,
            supportingText = if (wrong) ({ Text("Wrong password. Try again.") }) else null,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) { Text("Open") }
        Text(
            "The file stays locked. A temporary unlocked copy exists only while you view it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PdfPages(vm: ViewerViewModel, state: ViewerState.Pdf, zoom: Float) {
    val listState = rememberLazyListState()
    val horizontal = rememberScrollState() // shared so all zoomed pages pan together
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageWidth = maxWidth * zoom
        val renderWidth = min(with(density) { pageWidth.toPx() }.toInt(), 2000).coerceAtLeast(200)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.pageSizes.size) { index ->
                val (pw, ph) = state.pageSizes[index]
                val pageHeight = pageWidth * ph / pw
                val bitmap by produceState<android.graphics.Bitmap?>(null, index, renderWidth) {
                    value = vm.render(index, renderWidth) ?: value
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontal),
                ) {
                    Box(
                        modifier = Modifier
                            .width(pageWidth)
                            .height(pageHeight)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = bitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        val current by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
        Text(
            "$current / ${state.pageSizes.size}",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PictureView(container: AppContainer, doc: DocumentEntity, zoom: Float) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            AsyncImage(
                model = container.fileStore.docFile(doc.fileName),
                contentDescription = doc.name,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.width(maxWidth * zoom),
            )
        }
    }
}
