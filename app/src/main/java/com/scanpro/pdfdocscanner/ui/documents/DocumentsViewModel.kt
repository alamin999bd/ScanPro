package com.scanpro.pdfdocscanner.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanpro.pdfdocscanner.core.OcrEngine
import com.scanpro.pdfdocscanner.data.DocumentRepository
import com.scanpro.pdfdocscanner.data.db.DocumentEntity
import com.scanpro.pdfdocscanner.data.db.FolderEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME("Name A–Z"),
    SIZE("Largest first"),
}

/** [items] are filtered + sorted; [totalInSource] is the unfiltered count (0 = truly empty). */
data class DocsUi(val items: List<DocumentEntity>, val totalInSource: Int)

/** What the OCR dialog shows. */
sealed interface OcrUi {
    data class Running(val name: String, val done: Int, val total: Int) : OcrUi
    data class Done(val name: String, val text: String) : OcrUi
    data class Failed(val message: String) : OcrUi
}

/** Serves both "My Documents" ([folderId] == null) and a single folder's contents. */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsViewModel(
    private val repo: DocumentRepository,
    private val ocr: OcrEngine,
    folderId: Long?,
) : ViewModel() {

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(SortOrder.NEWEST)

    private val source =
        if (folderId == null) repo.observeDocuments() else repo.observeDocumentsInFolder(folderId)

    /** Ids of documents whose recognized (OCR) text contains the search words. */
    private val textMatches =
        query.debounce(250).flatMapLatest { q ->
            val needle = q.trim()
            if (needle.isEmpty()) flowOf(emptyList<Long>()) else repo.observeTextMatches(needle)
        }

    val documents: StateFlow<DocsUi?> =
        combine(source, query, sort, textMatches) { list, q, order, matchedIds ->
            val needle = q.trim()
            val ids = matchedIds.toSet()
            val filtered = if (needle.isEmpty()) {
                list
            } else {
                list.filter { it.name.contains(needle, ignoreCase = true) || it.id in ids }
            }
            val sorted = when (order) {
                SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
                SortOrder.OLDEST -> filtered.sortedBy { it.createdAt }
                SortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
                SortOrder.SIZE -> filtered.sortedByDescending { it.sizeBytes }
            }
            DocsUi(sorted, list.size)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val folders: StateFlow<List<FolderEntity>> =
        repo.observeFolderList().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { query.value = q }
    fun setSort(order: SortOrder) { sort.value = order }

    fun rename(doc: DocumentEntity, name: String) {
        viewModelScope.launch { repo.renameDocument(doc, name) }
    }

    fun move(doc: DocumentEntity, folderId: Long?) {
        viewModelScope.launch { repo.moveDocument(doc, folderId) }
    }

    private val _ocr = MutableStateFlow<OcrUi?>(null)
    val ocrState: StateFlow<OcrUi?> = _ocr
    private var ocrJob: Job? = null

    /** Reads the text of a PDF or image offline and stores it so the document can be found by its content. */
    fun runOcr(doc: DocumentEntity) {
        if (ocrJob?.isActive == true) return
        if (doc.type == "pdf" && doc.pageCount == 0) {
            _ocr.value = OcrUi.Failed("This PDF is password-protected. Remove the password in PDF Tools first.")
            return
        }
        ocrJob = viewModelScope.launch {
            _ocr.value = OcrUi.Running(doc.name, 0, 0)
            try {
                val text = ocr.extractText(repo.fileOf(doc), doc.type) { done, total ->
                    _ocr.value = OcrUi.Running(doc.name, done, total)
                }
                if (text.isBlank()) {
                    _ocr.value = OcrUi.Failed("No readable text was found in \"${doc.name}\".")
                } else {
                    repo.saveText(doc.id, text)
                    _ocr.value = OcrUi.Done(doc.name, text)
                }
            } catch (e: CancellationException) {
                _ocr.value = null
                throw e
            } catch (e: Exception) {
                _ocr.value = OcrUi.Failed(e.message ?: "The text could not be read.")
            } catch (e: OutOfMemoryError) {
                _ocr.value = OcrUi.Failed("This document is too large to read on this phone.")
            }
        }
    }

    fun cancelOcr() {
        ocrJob?.cancel()
        _ocr.value = null
    }

    fun dismissOcr() { _ocr.value = null }

    fun delete(doc: DocumentEntity) {
        viewModelScope.launch { repo.deleteDocument(doc) }
    }
}
