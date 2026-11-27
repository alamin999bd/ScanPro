package com.scanpro.pdfdocscanner.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanpro.pdfdocscanner.core.FileStore
import com.scanpro.pdfdocscanner.core.PdfEngine
import com.scanpro.pdfdocscanner.core.OcrEngine
import com.scanpro.pdfdocscanner.data.DocumentRepository
import com.scanpro.pdfdocscanner.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ScanUi(
    /** Captured pages (temp files). In ID mode: index 0 = front, 1 = back. */
    val pages: List<File> = emptyList(),
    /** Index of the page open in the editor, or null. */
    val editing: Int? = null,
    val saving: Boolean = false,
    val savedName: String? = null,
    val savedId: Long? = null,
    val error: String? = null,
)

class ScanViewModel(
    private val repo: DocumentRepository,
    private val files: FileStore,
    private val pdf: PdfEngine,
    private val ocr: OcrEngine,
    private val settings: SettingsRepository,
    private val isIdMode: Boolean,
) : ViewModel() {

    private val maxPages = if (isIdMode) 2 else Int.MAX_VALUE
    private var galleryImportInProgress = false
    private val _ui = MutableStateFlow(ScanUi())
    val ui: StateFlow<ScanUi> = _ui

    fun newCaptureFile(): File = files.newTempFile("jpg")

    fun onCaptured(file: File) {
        _ui.update { state ->
            val pages = state.pages + file
            state.copy(pages = pages, error = null, editing = if (isIdMode) pages.lastIndex else state.editing)
        }
    }

    fun onCaptureFailed(file: File, message: String) {
        file.delete()
        _ui.update { it.copy(error = message) }
    }

    /** Copies gallery images into scratch files so they can be edited like camera captures. */
    fun addFromGallery(uris: List<Uri>) {
        if (uris.isEmpty() || galleryImportInProgress) return
        galleryImportInProgress = true
        viewModelScope.launch {
            val accepted = uris.take((maxPages - _ui.value.pages.size).coerceAtLeast(0))
            val copied = ArrayList<File>()
            var failed = 0
            try {
                withContext(Dispatchers.IO) {
                    accepted.forEach { uri ->
                        val dest = files.newTempFile("jpg")
                        try {
                            files.copyTo(uri, dest)
                            copied.add(dest)
                        } catch (cancelled: CancellationException) {
                            dest.delete()
                            throw cancelled
                        } catch (ignored: Exception) {
                            dest.delete()
                            failed++
                        }
                    }
                }
                _ui.update { state ->
                    state.copy(
                        pages = state.pages + copied,
                        error = when {
                            failed > 0 -> "$failed image(s) could not be read."
                            uris.size > accepted.size -> "Only ${accepted.size} image(s) were added (ID scan holds front and back)."
                            else -> null
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                copied.forEach { it.delete() }
                throw cancelled
            } finally {
                galleryImportInProgress = false
            }
        }
    }

    fun openEditor(index: Int) = _ui.update { it.copy(editing = index) }
    fun closeEditor() = _ui.update { it.copy(editing = null) }

    /** Replaces a page with the edited file and removes the old scratch file. */
    fun replacePage(index: Int, newFile: File) = _ui.update { state ->
        val old = state.pages.getOrNull(index)
        if (old == null) {
            newFile.delete()
            return@update state.copy(editing = null)
        }
        old.delete()
        state.copy(
            pages = state.pages.toMutableList().also { it[index] = newFile },
            editing = null,
        )
    }

    fun removePage(index: Int) = _ui.update { state ->
        state.pages.getOrNull(index)?.delete()
        state.copy(pages = state.pages.filterIndexed { i, _ -> i != index }, editing = null)
    }

    fun movePage(index: Int, delta: Int) = _ui.update { state ->
        val target = index + delta
        if (target !in state.pages.indices) return@update state
        val list = state.pages.toMutableList()
        list.add(target, list.removeAt(index))
        state.copy(pages = list)
    }

    fun clearError() = _ui.update { it.copy(error = null) }

    fun startOver() {
        _ui.value.pages.forEach { it.delete() }
        _ui.value = ScanUi()
    }

    /**
     * Save a readable image PDF immediately, then upgrade it to a searchable PDF in the
     * background. A completed image PDF always remains available if OCR or text-layer
     * generation fails.
     */
    fun save(name: String) {
        val state = _ui.value
        if (state.pages.isEmpty() || state.saving) return
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, error = null) }
            val out = files.newDocFile("pdf")
            try {
                val s = settings.settings.first()
                val sourcePages = state.pages.toList()
                val uris: List<Uri> = sourcePages.map { Uri.fromFile(it) }
                val finalName = name.ifBlank { FileStore.defaultScanName(if (isIdMode) "ID" else "Scan") }

                if (isIdMode) {
                    pdf.idCardToPdf(uris[0], uris.getOrNull(1), out, s.pageSize, s.imageQuality)
                } else {
                    pdf.imagesToPdf(uris, out, s.pageSize, s.imageQuality)
                }

                require(out.exists() && out.length() > 0L) { "PDF export produced an empty file." }
                val pageCount = if (isIdMode) 1 else uris.size
                val id = repo.addDocument(finalName, out, "pdf", pageCount)
                _ui.value = ScanUi(savedName = FileStore.sanitizeName(finalName), savedId = id)

                // OCR keeps the original scratch images until the searchable replacement is ready.
                // The first PDF is already registered and usable throughout this best-effort task.
                viewModelScope.launch {
                    var searchable: File? = null
                    try {
                        if (!isIdMode) {
                            val ocrPages = ArrayList<OcrPage>(sourcePages.size)
                            val recognizedText = StringBuilder()
                            sourcePages.forEach { image ->
                                val page = ocr.recognizeImagePage(image)
                                ocrPages.add(page)
                                page.words.forEach { word -> recognizedText.append(word.text).append(' ') }
                                recognizedText.append('\n')
                            }
                            if (ocrPages.size == sourcePages.size && ocrPages.any { it.words.isNotEmpty() }) {
                                searchable = files.newDocFile("pdf")
                                pdf.imagesToSearchablePdf(
                                    uris, ocrPages, searchable, s.pageSize, s.imageQuality
                                )
                                require(searchable.exists() && searchable.length() > 0L) {
                                    "Searchable PDF generation produced an empty file."
                                }
                                require(pdf.pageCount(searchable) == pageCount) {
                                    "Searchable PDF page count changed during export."
                                }
                                files.replaceDocumentAtomically(searchable, out)
                                searchable = null
                                repo.refreshDocumentSize(id, out)
                            }
                            val text = recognizedText.toString().trim()
                            if (text.isNotBlank()) repo.saveText(id, text)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (ignored: Exception) {
                        // Keep the already-saved image PDF intact if OCR/text-layer generation fails.
                    } finally {
                        searchable?.delete()
                        sourcePages.forEach { it.delete() }
                    }
                }
            } catch (cancelled: CancellationException) {
                out.delete()
                state.pages.forEach { it.delete() }
                _ui.update { it.copy(saving = false, error = "PDF creation was cancelled.") }
                throw cancelled
            } catch (e: Exception) {
                out.delete()
                state.pages.forEach { it.delete() }
                _ui.update { it.copy(saving = false, error = e.message ?: "Could not save the scan") }
            }
        }
    }

    override fun onCleared() {
        // Unsaved captures are scratch files.
        _ui.value.pages.forEach { it.delete() }
    }
}
