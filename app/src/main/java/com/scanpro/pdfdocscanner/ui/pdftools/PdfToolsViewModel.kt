package com.scanpro.pdfdocscanner.ui.pdftools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanpro.pdfdocscanner.core.FileStore
import com.scanpro.pdfdocscanner.core.PdfEngine
import com.scanpro.pdfdocscanner.core.parsePageRanges
import com.scanpro.pdfdocscanner.data.DocumentRepository
import com.scanpro.pdfdocscanner.data.db.DocumentEntity
import com.scanpro.pdfdocscanner.ui.components.formatFileSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ToolMessage(val text: String, val isError: Boolean)

class PdfToolsViewModel(
    private val repo: DocumentRepository,
    private val files: FileStore,
    private val pdf: PdfEngine,
) : ViewModel() {

    /** null while loading. */
    val pdfs: StateFlow<List<DocumentEntity>?> =
        repo.observeDocuments()
            .map { list -> list.filter { it.type == "pdf" } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy

    private val _message = MutableStateFlow<ToolMessage?>(null)
    val message: StateFlow<ToolMessage?> = _message

    fun dismissMessage() { _message.value = null }

    fun merge(docs: List<DocumentEntity>, name: String) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = "Merging PDFs…"
            val out = files.newDocFile("pdf")
            try {
                val pages = pdf.merge(docs.map { repo.fileOf(it) }, out)
                val finalName = name.ifBlank { FileStore.defaultScanName("Merged") }
                repo.addDocument(finalName, out, "pdf", pages)
                _message.value = ToolMessage(
                    "Merged ${docs.size} PDFs into \"${FileStore.sanitizeName(finalName)}\" ($pages pages). It is saved in My Documents.",
                    false,
                )
            } catch (e: Exception) {
                out.delete()
                _message.value = ToolMessage(e.message ?: "The PDFs could not be merged.", true)
            } finally {
                _busy.value = null
            }
        }
    }

    fun extract(doc: DocumentEntity, rangeText: String, name: String) {
        if (_busy.value != null) return
        if (doc.pageCount == 0) {
            _message.value = ToolMessage("This PDF is password-protected. Use \"Remove password\" first.", true)
            return
        }
        val pages = parsePageRanges(rangeText, doc.pageCount)
        if (pages == null) {
            _message.value = ToolMessage(
                "Enter pages between 1 and ${doc.pageCount}, like 1-3, 5.",
                true,
            )
            return
        }
        viewModelScope.launch {
            _busy.value = "Extracting pages…"
            val out = files.newDocFile("pdf")
            try {
                val count = pdf.extractPages(repo.fileOf(doc), pages, out)
                val finalName = name.ifBlank { "${doc.name} (extract)" }
                repo.addDocument(finalName, out, "pdf", count)
                _message.value = ToolMessage(
                    "Created \"${FileStore.sanitizeName(finalName)}\" with $count ${if (count == 1) "page" else "pages"}. It is saved in My Documents.",
                    false,
                )
            } catch (e: Exception) {
                out.delete()
                _message.value = ToolMessage(e.message ?: "The pages could not be extracted.", true)
            } finally {
                _busy.value = null
            }
        }
    }

    fun deletePages(doc: DocumentEntity, rangeText: String, name: String) {
        if (_busy.value != null) return
        val pages = parsePageRanges(rangeText, doc.pageCount)
        if (pages == null) {
            _message.value = ToolMessage("Enter pages between 1 and ${doc.pageCount}, like 2-3, 5.", true)
            return
        }
        viewModelScope.launch {
            _busy.value = "Deleting pages…"
            val out = files.newDocFile("pdf")
            try {
                val count = pdf.deletePages(repo.fileOf(doc), pages, out)
                val finalName = name.ifBlank { "${doc.name} (edited)" }
                repo.addDocument(finalName, out, "pdf", count)
                _message.value = ToolMessage("Saved a copy with $count pages. The original is unchanged.", false)
            } catch (e: Exception) {
                out.delete()
                _message.value = ToolMessage(e.message ?: "Pages could not be deleted.", true)
            } finally { _busy.value = null }
        }
    }

    fun reorderPages(doc: DocumentEntity, orderText: String, name: String) {
        if (_busy.value != null) return
        val order = orderText.split(',', ' ', ';').filter { it.isNotBlank() }.map { it.toIntOrNull() }
        if (doc.pageCount <= 0 || order.any { it == null } || order.size != doc.pageCount ||
            order.filterNotNull().toSet().size != doc.pageCount || order.filterNotNull().any { it !in 1..doc.pageCount }) {
            _message.value = ToolMessage("Enter all pages exactly once, separated by commas. Example: 3,1,2", true)
            return
        }
        viewModelScope.launch {
            _busy.value = "Reordering pages…"
            val out = files.newDocFile("pdf")
            try {
                val count = pdf.reorderPages(repo.fileOf(doc), order.filterNotNull(), out)
                val finalName = name.ifBlank { "${doc.name} (reordered)" }
                repo.addDocument(finalName, out, "pdf", count)
                _message.value = ToolMessage("Saved the reordered $count-page copy. The original is unchanged.", false)
            } catch (e: Exception) {
                out.delete()
                _message.value = ToolMessage(e.message ?: "Pages could not be reordered.", true)
            } finally { _busy.value = null }
        }
    }

    fun rotatePages(doc: DocumentEntity, degrees: Int, name: String) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = "Rotating pages…"
            val out = files.newDocFile("pdf")
            try {
                val count = pdf.rotatePages(repo.fileOf(doc), degrees, out)
                val finalName = name.ifBlank { "${doc.name} (rotated)" }
                repo.addDocument(finalName, out, "pdf", count)
                _message.value = ToolMessage("Rotated all $count pages by $degrees° in a new copy.", false)
            } catch (e: Exception) {
                out.delete()
                _message.value = ToolMessage(e.message ?: "Pages could not be rotated.", true)
            } finally { _busy.value = null }
        }
    }

    fun addPageNumbers(doc: DocumentEntity, name: String) {
        if (_busy.value != null) return
        if (doc.pageCount <= 0) {
            _message.value = ToolMessage("Unlock this PDF first.", true)
            return
        }
        viewModelScope.launch {
            _busy.value = "Adding page numbers…"
            val out = files.newDocFile("pdf")
            try {
                val count = pdf.addPageNumbers(repo.fileOf(doc), out)
                val finalName = name.ifBlank { "${doc.name} (numbered)" }
                repo.addDocument(finalName, out, "pdf", count)
                _message.value = ToolMessage("Added page numbers to $count pages. Original unchanged.", false)
            } catch (e: Exception) {
                out.delete()
                _message.value = ToolMessage(e.message ?: "Page numbers could not be added.", true)
            } finally { _busy.value = null }
        }
    }

    fun searchPdfText(doc: DocumentEntity, query: String) {
        if (_busy.value != null) return
        if (query.isBlank()) {
            _message.value = ToolMessage("Enter a word or phrase to search.", true)
            return
        }
        viewModelScope.launch {
            _busy.value = "Searching PDF text…"
            try {
                val matches = pdf.searchText(repo.fileOf(doc), query)
                _message.value = if (matches.isEmpty()) {
                    ToolMessage("No selectable/searchable text matching “$query” was found. Scanned image-only pages need OCR text embedded in the PDF.", false)
                } else {
                    ToolMessage("Found “$query” on page(s): ${matches.joinToString { it.first.toString() }}", false)
                }
            } catch (e: Exception) {
                _message.value = ToolMessage(e.message ?: "PDF text search failed.", true)
            } finally { _busy.value = null }
        }
    }

    fun addBookmark(doc: DocumentEntity, title: String, pageText: String, name: String) {
        if (_busy.value != null) return
        val page = pageText.toIntOrNull()
        if (doc.pageCount <= 0 || page == null || page !in 1..doc.pageCount) {
            _message.value = ToolMessage("Enter a page between 1 and ${doc.pageCount}.", true)
            return
        }
        if (title.isBlank()) {
            _message.value = ToolMessage("Enter a bookmark title.", true)
            return
        }
        viewModelScope.launch {
            _busy.value = "Adding PDF bookmark…"
            val out = files.newDocFile("pdf")
            try {
                val count = pdf.addBookmark(repo.fileOf(doc), out, title, page)
                val finalName = name.ifBlank { "${doc.name} (bookmarked)" }
                repo.addDocument(finalName, out, "pdf", count)
                _message.value = ToolMessage("Added bookmark “${title.trim()}” for page $page in a new PDF copy. Original unchanged.", false)
            } catch (e: Exception) {
                out.delete()
                _message.value = ToolMessage(e.message ?: "The bookmark could not be added.", true)
            } finally { _busy.value = null }
        }
    }

    fun addMarkup(doc: DocumentEntity, pageText: String, kind: String, note: String, xText: String, yText: String, widthText: String, heightText: String, name: String) {
        if (_busy.value != null) return
        val page = pageText.toIntOrNull()
        val x = xText.toFloatOrNull()
        val y = yText.toFloatOrNull()
        val width = widthText.toFloatOrNull()
        val height = heightText.toFloatOrNull()
        if (doc.pageCount <= 0 || page == null || page !in 1..doc.pageCount ||
            x == null || y == null || width == null || height == null) {
            _message.value = ToolMessage("Enter a valid page and numeric area values.", true)
            return
        }
        if (kind == "NOTE" && note.isBlank()) {
            _message.value = ToolMessage("Enter the note text.", true)
            return
        }
        viewModelScope.launch {
            _busy.value = if (kind == "NOTE") "Adding text note…" else "Adding highlight…"
            val out = files.newDocFile("pdf")
            try {
                val count = pdf.addPageMarkup(repo.fileOf(doc), out, page, kind, note, x, y, width, height)
                val suffix = if (kind == "NOTE") "annotated" else "highlighted"
                val finalName = name.ifBlank { "${doc.name} ($suffix)" }
                repo.addDocument(finalName, out, "pdf", count)
                _message.value = ToolMessage("Saved a new PDF copy with the ${kind.lowercase()} on page $page. Original unchanged.", false)
            } catch (e: Exception) {
                out.delete()
                _message.value = ToolMessage(e.message ?: "The PDF markup could not be added.", true)
            } finally { _busy.value = null }
        }
    }

    fun toImages(doc: DocumentEntity) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = "Exporting pages…"
            val created = ArrayList<java.io.File>()
            try {
                val images = pdf.pdfToImages(
                    input = repo.fileOf(doc),
                    newFile = { files.newDocFile("jpg").also { created.add(it) } },
                    onProgress = { done, total -> _busy.value = "Exporting page $done of $total…" },
                )
                images.forEachIndexed { i, file ->
                    repo.addDocument("${doc.name} - page ${i + 1}", file, "image", 1)
                }
                _message.value = ToolMessage(
                    "Saved ${images.size} ${if (images.size == 1) "image" else "images"} to My Documents.",
                    false,
                )
            } catch (e: Exception) {
                created.forEach { it.delete() }
                _message.value = ToolMessage(e.message ?: "The PDF could not be exported.", true)
            } finally {
                _busy.value = null
            }
        }
    }
    private fun describe(e: Exception, fallback: String): String =
        if (e.javaClass.simpleName == "InvalidPasswordException") {
            "The password is wrong, or this PDF is protected and needs its password."
        } else {
            e.message ?: fallback
        }

    /** [level]: 0 = smaller, 1 = balanced, 2 = better quality. The original is never changed. */
    fun compress(doc: DocumentEntity, level: Int, name: String) {
        if (_busy.value != null) return
        val (quality, width) = when (level) {
            0 -> 50 to 1000
            2 -> 85 to 1654
            else -> 70 to 1240
        }
        viewModelScope.launch {
            _busy.value = "Compressing…"
            val out = files.newDocFile("pdf")
            try {
                val source = repo.fileOf(doc)
                val before = source.length()
                val pages = pdf.compress(source, out, quality, width) { done, total ->
                    _busy.value = "Compressing page $done of $total…"
                }
                val after = out.length()
                if (after >= before) {
                    out.delete()
                    _message.value = ToolMessage(
                        "Nothing was saved: the compressed copy (${formatFileSize(after)}) would not be smaller " +
                            "than the original (${formatFileSize(before)}). Try \"Smaller\".",
                        false,
                    )
                } else {
                    val finalName = name.ifBlank { "${doc.name} (compressed)" }
                    repo.addDocument(finalName, out, "pdf", pages)
                    _message.value = ToolMessage(
                        "Saved \"${FileStore.sanitizeName(finalName)}\": ${formatFileSize(before)} → ${formatFileSize(after)}. " +
                            "The original is unchanged.",
                        false,
                    )
                }
            } catch (e: Exception) {
                out.delete()
                _message.value = ToolMessage(describe(e, "The PDF could not be compressed."), true)
            } catch (e: OutOfMemoryError) {
                out.delete()
                _message.value = ToolMessage("This PDF is too large to compress on this phone.", true)
            } finally {
                _busy.value = null
            }
        }
    }

    fun lock(doc: DocumentEntity, password: String, name: String) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = "Locking PDF…"
            val out = files.newDocFile("pdf")
            try {
                pdf.protect(repo.fileOf(doc), out, password)
                val finalName = name.ifBlank { "${doc.name} (locked)" }
                // pageCount 0 marks a password-protected file in the lists.
                repo.addDocument(finalName, out, "pdf", 0)
                _message.value = ToolMessage(
                    "Saved \"${FileStore.sanitizeName(finalName)}\" with a password. Keep the password safe, it cannot be recovered.",
                    false,
                )
            } catch (e: Exception) {
                out.delete()
                _message.value = ToolMessage(describe(e, "The PDF could not be locked."), true)
            } finally {
                _busy.value = null
            }
        }
    }

    fun unlock(doc: DocumentEntity, password: String, name: String) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = "Removing password…"
            val out = files.newDocFile("pdf")
            try {
                val pages = pdf.unlock(repo.fileOf(doc), out, password)
                val finalName = name.ifBlank { "${doc.name} (unlocked)" }
                repo.addDocument(finalName, out, "pdf", pages)
                _message.value = ToolMessage(
                    "Saved \"${FileStore.sanitizeName(finalName)}\" without a password ($pages pages).",
                    false,
                )
            } catch (e: Exception) {
                out.delete()
                _message.value = ToolMessage(describe(e, "The password could not be removed."), true)
            } finally {
                _busy.value = null
            }
        }
    }

    fun watermark(doc: DocumentEntity, text: String, name: String) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = "Adding watermark…"
            val out = files.newDocFile("pdf")
            try {
                val pages = pdf.watermark(repo.fileOf(doc), out, text)
                val finalName = name.ifBlank { "${doc.name} (watermark)" }
                repo.addDocument(finalName, out, "pdf", pages)
                _message.value = ToolMessage(
                    "Saved \"${FileStore.sanitizeName(finalName)}\" with the watermark on $pages ${if (pages == 1) "page" else "pages"}.",
                    false,
                )
            } catch (e: Exception) {
                out.delete()
                _message.value = ToolMessage(describe(e, "The watermark could not be added."), true)
            } finally {
                _busy.value = null
            }
        }
    }

    /** [pageText] empty means the last page. */
    fun sign(doc: DocumentEntity, signature: android.graphics.Bitmap, pageText: String, position: Int, name: String) {
        if (_busy.value != null) return
        val page = if (pageText.isBlank()) doc.pageCount else pageText.toIntOrNull()
        if (page == null || page < 1 || page > doc.pageCount) {
            _message.value = ToolMessage("Enter a page between 1 and ${doc.pageCount}.", true)
            return
        }
        viewModelScope.launch {
            _busy.value = "Adding signature…"
            val out = files.newDocFile("pdf")
            try {
                val pages = pdf.sign(repo.fileOf(doc), out, signature, page, position)
                val finalName = name.ifBlank { "${doc.name} (signed)" }
                repo.addDocument(finalName, out, "pdf", pages)
                _message.value = ToolMessage(
                    "Saved \"${FileStore.sanitizeName(finalName)}\" with your signature on page $page.",
                    false,
                )
            } catch (e: Exception) {
                out.delete()
                _message.value = ToolMessage(describe(e, "The signature could not be added."), true)
            } finally {
                _busy.value = null
            }
        }
    }
}
