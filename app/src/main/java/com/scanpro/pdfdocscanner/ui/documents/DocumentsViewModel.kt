package com.scanpro.pdfdocscanner.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanpro.pdfdocscanner.data.DocumentRepository
import com.scanpro.pdfdocscanner.data.db.DocumentEntity
import com.scanpro.pdfdocscanner.data.db.FolderEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

/** Serves both "My Documents" ([folderId] == null) and a single folder's contents. */
class DocumentsViewModel(
    private val repo: DocumentRepository,
    folderId: Long?,
) : ViewModel() {

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(SortOrder.NEWEST)

    private val source =
        if (folderId == null) repo.observeDocuments() else repo.observeDocumentsInFolder(folderId)

    val documents: StateFlow<DocsUi?> =
        combine(source, query, sort) { list, q, order ->
            val needle = q.trim()
            val filtered = if (needle.isEmpty()) list else list.filter { it.name.contains(needle, ignoreCase = true) }
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

    fun delete(doc: DocumentEntity) {
        viewModelScope.launch { repo.deleteDocument(doc) }
    }
}
