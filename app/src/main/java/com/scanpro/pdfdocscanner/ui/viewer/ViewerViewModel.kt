package com.scanpro.pdfdocscanner.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanpro.pdfdocscanner.core.FileStore
import com.scanpro.pdfdocscanner.core.PdfEngine
import com.scanpro.pdfdocscanner.core.PdfBookmark
import com.scanpro.pdfdocscanner.core.PdfPageMarkup
import com.scanpro.pdfdocscanner.data.DocumentRepository
import com.scanpro.pdfdocscanner.data.db.DocumentEntity
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ViewerState {
    data object Loading : ViewerState
    data class Failed(val message: String) : ViewerState
    data class NeedsPassword(val doc: DocumentEntity, val wrongPassword: Boolean) : ViewerState
    /** [pageSizes] are the PDF's page width/height in points, used to reserve layout space. */
    data class Pdf(val doc: DocumentEntity, val pageSizes: List<Pair<Int, Int>>, val bookmarks: List<PdfBookmark>, val pageMarkups: List<List<PdfPageMarkup>>) : ViewerState
    data class Picture(val doc: DocumentEntity) : ViewerState
}

class ViewerViewModel(
    private val repo: DocumentRepository,
    private val docId: Long,
    private val files: FileStore,
    private val pdf: PdfEngine,
) : ViewModel() {

    private var currentDoc: DocumentEntity? = null
    private var tempCopy: java.io.File? = null
    private var openedFile: java.io.File? = null

    private val _state = MutableStateFlow<ViewerState>(ViewerState.Loading)
    val state: StateFlow<ViewerState> = _state

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    /** PdfRenderer allows one open page at a time, so every render goes through this lock. */
    private val renderLock = Mutex()
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val doc = repo.getDocument(docId)
        if (doc == null) {
            _state.value = ViewerState.Failed("This document no longer exists.")
            return
        }
        currentDoc = doc
        if (doc.type != "pdf") {
            _state.value = ViewerState.Picture(doc)
            return
        }
        val file = repo.fileOf(doc)
        try {
            _state.value = showFile(doc, file)
        } catch (e: Exception) {
            val locked = withContext(Dispatchers.IO) { pdf.isPasswordProtected(file) }
            _state.value = if (locked) {
                ViewerState.NeedsPassword(doc, wrongPassword = false)
            } else {
                ViewerState.Failed("This PDF could not be opened. It may be damaged.")
            }
        }
    }

    /** Opens [file] with PdfRenderer and returns the viewer state. Throws if it cannot be opened. */
    private suspend fun showFile(doc: DocumentEntity, file: java.io.File): ViewerState {
        openedFile = file
        val sizes = withContext(Dispatchers.IO) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = try {
                PdfRenderer(fd)
            } catch (e: Exception) {
                runCatching { fd.close() }
                throw e
            }
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
            cache.evictAll()
            pfd = fd
            renderer = r
            (0 until r.pageCount).map { i -> r.openPage(i).use { it.width to it.height } }
        }
        val bookmarks = withContext(Dispatchers.IO) { pdf.readBookmarks(file) }
        val pageMarkups = runCatching { pdf.readPageMarkups(file) }.getOrDefault(emptyList())
        return if (sizes.isEmpty()) ViewerState.Failed("This PDF has no pages.") else ViewerState.Pdf(doc, sizes, bookmarks, pageMarkups)
    }

    /**
     * Opens a password-protected PDF. A decrypted copy is written to the app's temporary cache only
     * while this screen is open, and is deleted when it closes. The saved document stays locked.
     */
    fun submitPassword(password: String) {
        val doc = currentDoc ?: return
        if (password.isEmpty()) return
        viewModelScope.launch {
            _state.value = ViewerState.Loading
            val temp = files.newTempFile("pdf")
            try {
                withContext(Dispatchers.IO) { pdf.unlock(repo.fileOf(doc), temp, password) }
                tempCopy?.delete()
                tempCopy = temp
                _state.value = showFile(doc, temp)
            } catch (e: Exception) {
                temp.delete()
                _state.value = if (e.javaClass.simpleName == "InvalidPasswordException") {
                    ViewerState.NeedsPassword(doc, wrongPassword = true)
                } else {
                    ViewerState.Failed("This PDF could not be opened. It may be damaged.")
                }
            }
        }
    }

    /** Extracts selectable/OCR text from the currently opened PDF for an explicitly requested AI action. */
    suspend fun extractDocumentText(maxChars: Int = 24_000): String = withContext(Dispatchers.IO) {
        require(maxChars in 1..24_000) { "Text limit is invalid" }
        val file = openedFile ?: throw IllegalStateException("PDF is not ready")
        PDDocument.load(file).use { document ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val text = stripper.getText(document).trim()
            if (text.isBlank()) throw IllegalStateException("No selectable text found. This PDF may be image-only; run OCR first.")
            text.take(maxChars)
        }
    }

    /** Returns 1-based page numbers containing the query in selectable PDF text. */
    suspend fun searchPages(query: String): List<Pair<Int, String>> {
        require(query.isNotBlank()) { "Enter text to search" }
        val file = openedFile ?: throw IllegalStateException("PDF is not ready")
        return pdf.searchText(file, query)
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? {
        val safeWidth = widthPx.coerceIn(200, MAX_RENDER_WIDTH)
        val key = "$index@$safeWidth"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            renderLock.withLock {
                // A second page request may have populated the cache while this request waited.
                cache.get(key)?.let { return@withLock it }
                val r = renderer ?: return@withLock null
                try {
                    r.openPage(index).use { page ->
                        val h = (page.height.toFloat() * safeWidth / page.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(safeWidth, h, Bitmap.Config.ARGB_8888)
                        try {
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            cache.put(key, bmp)
                            bmp
                        } catch (error: Exception) {
                            bmp.recycle()
                            throw error
                        }
                    }
                } catch (ignored: Exception) {
                    null
                }
            }
        }
    }

    override fun onCleared() {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        renderer = null
        pfd = null
        cache.evictAll()
        tempCopy?.delete()
        tempCopy = null
        openedFile = null
    }

    companion object {
        private const val CACHE_BYTES = 48 * 1024 * 1024
        private const val MAX_RENDER_WIDTH = 1600
    }
}
