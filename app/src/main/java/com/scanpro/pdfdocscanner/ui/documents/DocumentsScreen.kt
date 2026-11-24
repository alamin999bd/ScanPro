package com.scanpro.pdfdocscanner.ui.documents

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.ui.components.ScanProTopBar
import com.scanpro.pdfdocscanner.ui.components.containerViewModel

@Composable
fun DocumentsScreen(
    container: AppContainer,
    focusSearch: Boolean,
    onBack: () -> Unit,
    onOpenViewer: (Long) -> Unit,
) {
    val vm = containerViewModel(container, key = "all_documents") { DocumentsViewModel(it.documents, null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScanProTopBar(title = "My Documents", onBack = onBack) },
    ) { padding ->
        DocumentListContent(
            container = container,
            vm = vm,
            emptyIcon = Icons.Rounded.Description,
            emptyTitle = "No documents yet",
            emptyMessage = "Scan a page, convert images to PDF, or import a file. Everything you create appears here.",
            onOpenViewer = onOpenViewer,
            modifier = Modifier.padding(padding),
            focusSearch = focusSearch,
        )
    }
}
