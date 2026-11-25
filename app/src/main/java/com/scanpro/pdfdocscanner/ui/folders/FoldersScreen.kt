package com.scanpro.pdfdocscanner.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.data.DocumentRepository
import com.scanpro.pdfdocscanner.data.db.FolderWithCount
import com.scanpro.pdfdocscanner.ui.components.ConfirmDialog
import com.scanpro.pdfdocscanner.ui.components.EmptyState
import com.scanpro.pdfdocscanner.ui.components.ScanProTopBar
import com.scanpro.pdfdocscanner.ui.components.TextInputDialog
import com.scanpro.pdfdocscanner.ui.components.containerViewModel
import com.scanpro.pdfdocscanner.ui.documents.DocumentListContent
import com.scanpro.pdfdocscanner.ui.documents.DocumentsViewModel
import com.scanpro.pdfdocscanner.ui.theme.TileFolders
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoldersViewModel(private val repo: DocumentRepository) : ViewModel() {
    val folders: StateFlow<List<FolderWithCount>?> =
        repo.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String) { viewModelScope.launch { repo.createFolder(name) } }
    fun rename(id: Long, name: String) { viewModelScope.launch { repo.renameFolder(id, name) } }
    fun delete(id: Long) { viewModelScope.launch { repo.deleteFolder(id) } }
}

@Composable
fun FoldersScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenFolder: (Long) -> Unit,
) {
    val vm = containerViewModel(container) { FoldersViewModel(it.documents) }
    val folders by vm.folders.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FolderWithCount?>(null) }
    var deleting by remember { mutableStateOf<FolderWithCount?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScanProTopBar(title = "Folders", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                text = { Text("New folder") },
            )
        },
    ) { padding ->
        val list = folders
        when {
            list == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Folder,
                title = "No folders yet",
                message = "Create folders to keep receipts, IDs, contracts and other documents organized.",
                modifier = Modifier.padding(padding),
                actionLabel = "Create a folder",
                onAction = { showCreate = true },
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { folder ->
                    FolderRow(
                        folder = folder,
                        onClick = { onOpenFolder(folder.id) },
                        onRename = { renaming = folder },
                        onDelete = { deleting = folder },
                    )
                }
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = "New folder",
            label = "Folder name",
            confirmLabel = "Create",
            onConfirm = { vm.create(it); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }
    renaming?.let { f ->
        TextInputDialog(
            title = "Rename folder",
            initialValue = f.name,
            label = "Folder name",
            onConfirm = { vm.rename(f.id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { f ->
        ConfirmDialog(
            title = "Delete folder?",
            message = "\"${f.name}\" will be removed. Its documents are kept and move back to My Documents.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(f.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun FolderRow(
    folder: FolderWithCount,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Folder, contentDescription = null, tint = TileFolders, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (folder.documentCount == 1) "1 document" else "${folder.documentCount} documents",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                        onClick = { expanded = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { expanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
fun FolderDetailScreen(
    container: AppContainer,
    folderId: Long,
    onBack: () -> Unit,
    onOpenViewer: (Long) -> Unit,
) {
    val vm = containerViewModel(container, key = "folder_$folderId") { DocumentsViewModel(it.documents, it.ocrEngine, folderId) }
    val name by produceState("Folder", folderId) {
        value = container.documents.getFolder(folderId)?.name ?: "Folder"
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScanProTopBar(title = name, onBack = onBack) },
    ) { padding ->
        DocumentListContent(
            container = container,
            vm = vm,
            emptyIcon = Icons.Rounded.Folder,
            emptyTitle = "This folder is empty",
            emptyMessage = "Open My Documents, tap the ⋮ menu on a file and choose \"Move to folder\".",
            onOpenViewer = onOpenViewer,
            modifier = Modifier.padding(padding),
        )
    }
}
