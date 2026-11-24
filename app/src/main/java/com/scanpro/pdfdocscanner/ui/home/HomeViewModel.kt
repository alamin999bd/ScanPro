package com.scanpro.pdfdocscanner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scanpro.pdfdocscanner.data.DocumentRepository
import com.scanpro.pdfdocscanner.data.db.DocumentEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repo: DocumentRepository) : ViewModel() {
    val documentCount: StateFlow<Int> =
        repo.observeDocumentCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val recent: StateFlow<List<DocumentEntity>> =
        repo.observeRecent(3).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
