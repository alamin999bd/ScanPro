package com.scanpro.pdfdocscanner.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
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
import kotlinx.coroutines.launch

private val ZOOM_LEVELS = listOf(1f, 1.5f, 2f, 3f)

/** In-app preview for PDFs (rendered offline with PdfRenderer) and image documents. */
@Composable
fun ViewerScreen(container: AppContainer, docId: Long, onBack: () -> Unit) {
    val vm = containerViewModel(container, key = "viewer_$docId") { ViewerViewModel(it.documents, docId, it.fileStore, it.pdfEngine) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val export = rememberDocExporter(container)
    var zoomIndex by remember { mutableIntStateOf(0) }
    var searchExpanded by remember { mutableStateOf(false) }

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
                        if (doc.type == "pdf") IconButton(onClick = { searchExpanded = !searchExpanded }) { Icon(Icons.Rounded.Search, contentDescription = "Search PDF") }
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
                is ViewerState.Pdf -> PdfPages(vm, s, ZOOM_LEVELS[zoomIndex], searchExpanded)
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
private fun PdfPages(vm: ViewerViewModel, state: ViewerState.Pdf, zoom: Float, searchExpanded: Boolean) {
    val listState = rememberLazyListState()
    val horizontal = rememberScrollState() // shared so all zoomed pages pan together
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var selectedNote by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageWidth = maxWidth * zoom
        val renderWidth = min(with(density) { pageWidth.toPx() }.toInt(), 2000).coerceAtLeast(200)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(top = when { searchExpanded && state.bookmarks.isNotEmpty() -> 148.dp; searchExpanded -> 100.dp; state.bookmarks.isNotEmpty() -> 52.dp; else -> 0.dp }),
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
                            val pageMarkups = state.pageMarkups.getOrNull(index).orEmpty()
                            if (pageMarkups.isNotEmpty()) {
                                var overlaySize by remember(index, renderWidth) { mutableStateOf(IntSize.Zero) }
                                Canvas(
                                    modifier = Modifier.fillMaxSize()
                                        .onSizeChanged { overlaySize = it }
                                        .pointerInput(pageMarkups, overlaySize) {
                                            detectTapGestures { tap ->
                                                selectedNote = pageMarkups.firstOrNull { markup ->
                                                    markup.kind == "NOTE" &&
                                                        tap.x >= markup.left * overlaySize.width &&
                                                        tap.x <= (markup.left + markup.width) * overlaySize.width &&
                                                        tap.y >= markup.top * overlaySize.height &&
                                                        tap.y <= (markup.top + markup.height) * overlaySize.height
                                                }?.note?.takeIf { it.isNotBlank() }
                                            }
                                        },
                                ) {
                                    pageMarkups.forEach { markup ->
                                        val topLeft = Offset(markup.left * size.width, markup.top * size.height)
                                        val markSize = Size(markup.width * size.width, markup.height * size.height)
                                        if (markup.kind == "HIGHLIGHT") {
                                            drawRect(
                                                color = Color(0xFFFFEB3B).copy(alpha = 0.38f),
                                                topLeft = topLeft,
                                                size = markSize,
                                            )
                                        } else if (markup.kind == "NOTE") {
                                            drawCircle(
                                                color = Color(0xFF2563EB),
                                                radius = 10.dp.toPx(),
                                                center = Offset(topLeft.x + markSize.width / 2f, topLeft.y + markSize.height / 2f),
                                            )
                                            drawCircle(
                                                color = Color.White,
                                                radius = 3.dp.toPx(),
                                                center = Offset(topLeft.x + markSize.width / 2f, topLeft.y + markSize.height / 2f),
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

        if (searchExpanded) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Find in this PDF") })
                    Button(enabled = query.isNotBlank() && !searching, onClick = {
                        scope.launch {
                            searching = true
                            searchMessage = null
                            try {
                                searchResults = vm.searchPages(query.trim())
                                searchMessage = if (searchResults.isEmpty()) "No selectable text found" else "Found on ${searchResults.size} page(s)"
                            } catch (error: Exception) {
                                searchMessage = error.message ?: "Search failed"
                                searchResults = emptyList()
                            } finally { searching = false }
                        }
                    }) { Text(if (searching) "…" else "Find") }
                }
                searchMessage?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (searchResults.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        searchResults.forEach { (page, _) ->
                            TextButton(onClick = { scope.launch { listState.animateScrollToItem((page - 1).coerceIn(0, state.pageSizes.lastIndex)) } }) { Text("Page $page") }
                        }
                    }
                }
            }
        }

        if (state.bookmarks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (searchExpanded) 100.dp else 0.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.bookmarks.forEach { bookmark ->
                    TextButton(onClick = {
                        scope.launch { listState.animateScrollToItem((bookmark.pageNumber - 1).coerceIn(0, state.pageSizes.lastIndex)) }
                    }) {
                        Text("${bookmark.title} · ${bookmark.pageNumber}")
                    }
                }
            }
        }

        if (selectedNote != null) {
            AlertDialog(
                onDismissRequest = { selectedNote = null },
                title = { Text("PDF Note") },
                text = { Text(selectedNote.orEmpty()) },
                confirmButton = {
                    TextButton(onClick = { selectedNote = null }) { Text("Close") }
                },
            )
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
        val pageWidth = maxWidth * zoom
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
                modifier = Modifier.width(pageWidth),
            )
        }
    }
}
