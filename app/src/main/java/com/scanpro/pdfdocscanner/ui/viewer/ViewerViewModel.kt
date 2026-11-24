package com.scanpro.pdfdocscanner.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanpro.pdfdocscanner.core.FileStore
import com.scanpro.pdfdocscanner.core.PdfEngine
import com.scanpro.pdfdocscanner.data.DocumentRepository
import com.scanpro.pdfdocscanner.data.db.DocumentEntity
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
    data class Pdf(val doc: DocumentEntity, val pageSizes: List<Pair<Int, Int>>) : ViewerState
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
        return if (sizes.isEmpty()) ViewerState.Failed("This PDF has no pages.") else ViewerState.Pdf(doc, sizes)
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

    suspend fun render(index: Int, widthPx: Int): Bitmap? {
        val key = "$index@$widthPx"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            renderLock.withLock {
                val r = renderer ?: return@withLock null
                try {
                    r.openPage(index).use { page ->
                        val h = (page.height.toFloat() * widthPx / page.width).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        cache.put(key, bmp)
                        bmp
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
    }

    companion object {
        private const val CACHE_BYTES = 48 * 1024 * 1024
    }
}
