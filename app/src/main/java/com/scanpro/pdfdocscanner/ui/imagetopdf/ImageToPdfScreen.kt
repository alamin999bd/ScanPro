package com.scanpro.pdfdocscanner.ui.imagetopdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.core.FileStore
import com.scanpro.pdfdocscanner.core.PdfEngine
import com.scanpro.pdfdocscanner.data.DocumentRepository
import com.scanpro.pdfdocscanner.data.SettingsRepository
import com.scanpro.pdfdocscanner.ui.components.EmptyState
import com.scanpro.pdfdocscanner.ui.components.ProgressDialog
import com.scanpro.pdfdocscanner.ui.components.ScanProTopBar
import com.scanpro.pdfdocscanner.ui.components.containerViewModel
import com.scanpro.pdfdocscanner.ui.editor.PageEditorScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ImageToPdfUi(
    /** Scratch copies of the chosen images, so each can be edited without touching the originals. */
    val images: List<File> = emptyList(),
    val name: String = FileStore.defaultScanName("Images"),
    val editing: Int? = null,
    val importing: Boolean = false,
    val progress: Pair<Int, Int>? = null,
    val error: String? = null,
    val createdId: Long? = null,
    val createdName: String? = null,
    val createdPages: Int = 0,
)

class ImageToPdfViewModel(
    private val repo: DocumentRepository,
    private val files: FileStore,
    private val pdf: PdfEngine,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ImageToPdfUi())
    val ui: StateFlow<ImageToPdfUi> = _ui

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(importing = true, error = null) }
            val copied = ArrayList<File>()
            var failed = 0
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val dest = files.newTempFile("jpg")
                    try {
                        files.copyTo(uri, dest)
                        copied.add(dest)
                    } catch (ignored: Exception) {
                        dest.delete()
                        failed++
                    }
                }
            }
            _ui.update {
                it.copy(
                    images = it.images + copied,
                    importing = false,
                    error = if (failed > 0) "$failed image(s) could not be read." else null,
                )
            }
        }
    }

    fun remove(index: Int) = _ui.update { state ->
        state.images.getOrNull(index)?.delete()
        state.copy(images = state.images.filterIndexed { i, _ -> i != index })
    }

    fun setName(name: String) = _ui.update { it.copy(name = name) }

    fun move(index: Int, delta: Int) = _ui.update { state ->
        val target = index + delta
        if (target !in state.images.indices) return@update state
        val list = state.images.toMutableList()
        list.add(target, list.removeAt(index))
        state.copy(images = list)
    }

    fun openEditor(index: Int) = _ui.update { it.copy(editing = index) }
    fun closeEditor() = _ui.update { it.copy(editing = null) }

    fun replaceImage(index: Int, newFile: File) = _ui.update { state ->
        val old = state.images.getOrNull(index)
        if (old == null) {
            newFile.delete()
            return@update state.copy(editing = null)
        }
        old.delete()
        state.copy(images = state.images.toMutableList().also { it[index] = newFile }, editing = null)
    }

    fun reset() {
        _ui.value.images.forEach { it.delete() }
        _ui.value = ImageToPdfUi()
    }

    fun create() {
        val current = _ui.value
        if (current.images.isEmpty() || current.progress != null) return
        viewModelScope.launch {
            _ui.update { it.copy(progress = 0 to current.images.size, error = null) }
            val out = files.newDocFile("pdf")
            try {
                val s = settings.settings.first()
                val pages = pdf.imagesToPdf(current.images.map { Uri.fromFile(it) }, out, s.pageSize, s.imageQuality) { done, total ->
                    _ui.update { it.copy(progress = done to total) }
                }
                require(out.isFile && out.length() > 0L) { "PDF export produced an empty file." }
                val name = current.name.ifBlank { FileStore.defaultScanName("Images") }
                val id = repo.addDocument(name, out, "pdf", pages)
                current.images.forEach { it.delete() }
                _ui.value = ImageToPdfUi(
                    createdId = id,
                    createdName = FileStore.sanitizeName(name),
                    createdPages = pages,
                )
            } catch (cancelled: CancellationException) {
                out.delete()
                throw cancelled
            } catch (e: Exception) {
                out.delete()
                _ui.update { it.copy(progress = null, error = e.message ?: "Could not create the PDF") }
            }
        }
    }

    override fun onCleared() {
        _ui.value.images.forEach { it.delete() }
    }
}

@Composable
fun ImageToPdfScreen(container: AppContainer, onBack: () -> Unit, onOpenViewer: (Long) -> Unit) {
    val vm = containerViewModel(container) {
        ImageToPdfViewModel(it.documents, it.fileStore, it.pdfEngine, it.settings)
    }
    val ui by vm.ui.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        vm.addImages(uris)
    }
    val pickImages = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val editingIndex = ui.editing
    val editingFile = editingIndex?.let { ui.images.getOrNull(it) }
    if (editingIndex != null && editingFile != null) {
        PageEditorScreen(
            container = container,
            file = editingFile,
            initialAspect = null,
            onCancel = vm::closeEditor,
            onApplied = { vm.replaceImage(editingIndex, it) },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScanProTopBar(title = "Image to PDF", onBack = onBack) },
    ) { padding ->
        val createdId = ui.createdId
        when {
            createdId != null -> SuccessView(
                name = ui.createdName.orEmpty(),
                pages = ui.createdPages,
                modifier = Modifier.padding(padding),
                onOpen = { onOpenViewer(createdId) },
                onAnother = { vm.reset() },
            )
            ui.images.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Image,
                title = "Pick your images",
                message = "Choose photos or screenshots. Each image becomes one page. You can crop, rotate and enhance every page before saving.",
                modifier = Modifier.padding(padding),
                actionLabel = "Choose images",
                onAction = pickImages,
            )
            else -> Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                OutlinedTextField(
                    value = ui.name,
                    onValueChange = vm::setName,
                    label = { Text("PDF name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(ui.images, key = { _, f -> f.name }) { index, file ->
                        ImageRow(
                            file = file,
                            index = index,
                            canMoveUp = index > 0,
                            canMoveDown = index < ui.images.lastIndex,
                            onEdit = { vm.openEditor(index) },
                            onUp = { vm.move(index, -1) },
                            onDown = { vm.move(index, 1) },
                            onRemove = { vm.remove(index) },
                        )
                    }
                }
                ui.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = pickImages) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add")
                    }
                    Button(onClick = vm::create, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create PDF (${ui.images.size})")
                    }
                }
            }
        }
    }

    if (ui.importing) ProgressDialog("Loading images…")
    ui.progress?.let { (done, total) ->
        ProgressDialog(
            message = "Creating page $done of $total…",
            progress = if (total > 0) done.toFloat() / total else null,
        )
    }
}

@Composable
private fun ImageRow(
    file: File,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = file,
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onEdit),
            )
            Spacer(Modifier.width(12.dp))
            Text("Page ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit page ${index + 1}") }
            IconButton(onClick = onUp, enabled = canMoveUp) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = onDown, enabled = canMoveDown) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun SuccessView(
    name: String,
    pages: Int,
    modifier: Modifier,
    onOpen: () -> Unit,
    onAnother: () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("PDF created", style = MaterialTheme.typography.titleLarge)
            Text(
                "\"$name\" · $pages ${if (pages == 1) "page" else "pages"} is saved in My Documents.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("View PDF") }
            OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth()) { Text("Create another") }
        }
    }
}
