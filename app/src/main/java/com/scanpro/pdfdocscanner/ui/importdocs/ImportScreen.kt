package com.scanpro.pdfdocscanner.ui.importdocs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.core.FileStore
import com.scanpro.pdfdocscanner.core.OfficeConverter
import com.scanpro.pdfdocscanner.core.PdfEngine
import com.scanpro.pdfdocscanner.data.DocumentRepository
import com.scanpro.pdfdocscanner.ui.components.ProgressDialog
import com.scanpro.pdfdocscanner.ui.components.ScanProTopBar
import com.scanpro.pdfdocscanner.ui.components.containerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportState {
    data object Idle : ImportState
    data class Working(val done: Int, val total: Int) : ImportState
    data class Finished(val imported: Int, val failed: List<String>) : ImportState
}

class ImportViewModel(
    private val repo: DocumentRepository,
    private val files: FileStore,
    private val pdf: PdfEngine,
    private val office: OfficeConverter,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var imported = 0
            val failed = ArrayList<String>()
            _state.value = ImportState.Working(0, uris.size)
            uris.forEachIndexed { index, uri ->
                val displayName = files.displayName(uri) ?: "Document ${index + 1}"
                val ok = withContext(Dispatchers.IO) { importOne(uri, displayName) }
                if (ok) imported++ else failed.add(displayName)
                _state.value = ImportState.Working(index + 1, uris.size)
            }
            _state.value = ImportState.Finished(imported, failed)
        }
    }

    private suspend fun importOne(uri: Uri, displayName: String): Boolean {
        val mime = files.mimeType(uri).orEmpty()
        val isPdf = mime == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true)
        val isImage = mime.startsWith("image/")
        val isDocx = displayName.endsWith(".docx", ignoreCase = true) || mime == DOCX_MIME
        val isXlsx = displayName.endsWith(".xlsx", ignoreCase = true) || mime == XLSX_MIME
        if (isDocx || isXlsx) return importOffice(uri, displayName, isDocx)
        if (!isPdf && !isImage) return false

        val ext = when {
            isPdf -> "pdf"
            mime == "image/png" -> "png"
            mime == "image/webp" -> "webp"
            else -> "jpg"
        }
        val dest = files.newDocFile(ext)
        return try {
            files.copyTo(uri, dest)
            if (isPdf) {
                // Corrupt files throw and are reported as failed. A password-protected PDF is kept
                // (pageCount 0) so it can be opened with PDF Tools -> Remove password.
                val pages = try {
                    pdf.pageCount(dest)
                } catch (e: Exception) {
                    if (e.javaClass.simpleName == "InvalidPasswordException") 0 else throw e
                }
                repo.addDocument(FileStore.stripExtension(displayName), dest, "pdf", pages)
            } else {
                repo.addDocument(FileStore.stripExtension(displayName), dest, "image", 1)
            }
            true
        } catch (ignored: Exception) {
            dest.delete()
            false
        }
    }

    /** Word/Excel are converted to a text-only PDF; the original file is not kept. */
    private suspend fun importOffice(uri: Uri, displayName: String, isDocx: Boolean): Boolean {
        val source = files.newTempFile(if (isDocx) "docx" else "xlsx")
        val dest = files.newDocFile("pdf")
        return try {
            files.copyTo(uri, source)
            val pages = if (isDocx) office.docxToPdf(source, dest) else office.xlsxToPdf(source, dest)
            repo.addDocument(FileStore.stripExtension(displayName), dest, "pdf", pages)
            true
        } catch (ignored: Exception) {
            dest.delete()
            false
        } finally {
            source.delete()
        }
    }

    fun reset() { _state.value = ImportState.Idle }

    private companion object {
        const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}

@Composable
fun ImportScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenDocuments: () -> Unit,
) {
    val vm = containerViewModel(container) { ImportViewModel(it.documents, it.fileStore, it.pdfEngine, it.officeConverter) }
    val state by vm.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.import(uris)
    }
    val mimeTypes = arrayOf(
        "application/pdf",
        "image/*",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScanProTopBar(title = "Import Documents", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.UploadFile,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text("Bring files into ScanPro", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pick PDFs, images, Word (.docx) or Excel (.xlsx) files from your phone, Downloads, or any storage app. " +
                    "Files are copied into the app so they keep working offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Word and Excel files become a basic text-only PDF: words and table cells are kept, " +
                    "but pictures, colours, charts and exact layout are not. Old .doc and .xls files are not supported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { picker.launch(mimeTypes) }, modifier = Modifier.fillMaxWidth()) {
                Text("Choose files")
            }

            (state as? ImportState.Finished)?.let { result ->
                Spacer(Modifier.height(24.dp))
                ResultCard(result, onOpenDocuments)
            }
        }
    }

    (state as? ImportState.Working)?.let { w ->
        ProgressDialog(
            message = "Importing ${w.done} of ${w.total}…",
            progress = if (w.total > 0) w.done.toFloat() / w.total else null,
        )
    }
}

@Composable
private fun ResultCard(result: ImportState.Finished, onOpenDocuments: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (result.imported > 0) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (result.imported == 1) "1 file imported" else "${result.imported} files imported",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (result.failed.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Could not import: " + result.failed.joinToString(", ") +
                        ". They may be unsupported, damaged or password-protected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (result.imported > 0) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onOpenDocuments) { Text("Open My Documents") }
            }
        }
    }
}
