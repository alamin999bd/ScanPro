package com.scanpro.pdfdocscanner.ui.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.data.db.DocumentEntity
import com.scanpro.pdfdocscanner.ui.components.ConfirmDialog
import com.scanpro.pdfdocscanner.ui.components.DocumentRow
import com.scanpro.pdfdocscanner.ui.components.EmptyState
import com.scanpro.pdfdocscanner.ui.components.TextInputDialog
import com.scanpro.pdfdocscanner.ui.components.openDocument
import com.scanpro.pdfdocscanner.ui.components.rememberDocExporter
import com.scanpro.pdfdocscanner.ui.components.shareDocument

/**
 * Shared list used by My Documents and folder screens: search, sort and
 * view / open-with / share / save-to-device / rename / move / delete for each file.
 * Nothing is deleted without an explicit confirmation.
 */
@Composable
fun DocumentListContent(
    container: AppContainer,
    vm: DocumentsViewModel,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyMessage: String,
    onOpenViewer: (Long) -> Unit,
    modifier: Modifier = Modifier,
    focusSearch: Boolean = false,
) {
    val context = LocalContext.current
    val ui by vm.documents.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val export = rememberDocExporter(container)

    var renaming by remember { mutableStateOf<DocumentEntity?>(null) }
    var deleting by remember { mutableStateOf<DocumentEntity?>(null) }
    var moving by remember { mutableStateOf<DocumentEntity?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    var focusHandled by remember { mutableStateOf(false) }

    val state = ui
    LaunchedEffect(state?.totalInSource, focusSearch) {
        if (focusSearch && !focusHandled && (state?.totalInSource ?: 0) > 0) {
            focusHandled = true
            runCatching { searchFocus.requestFocus() }
        }
    }

    when {
        state == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.totalInSource == 0 -> EmptyState(
            icon = emptyIcon,
            title = emptyTitle,
            message = emptyMessage,
            modifier = modifier,
        )
        else -> Column(modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search documents") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = androidx.compose.material3.MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f).focusRequester(searchFocus),
                )
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.Rounded.SortByAlpha, contentDescription = "Sort")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                leadingIcon = { RadioButton(selected = sort == order, onClick = null) },
                                onClick = { vm.setSort(order); sortMenu = false },
                            )
                        }
                    }
                }
            }

            if (state.items.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No matches",
                    message = "Nothing is named \"${query.trim()}\". Try a different word.",
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.items, key = { it.id }) { doc ->
                        DocumentRow(
                            doc = doc,
                            container = container,
                            onClick = { onOpenViewer(doc.id) },
                            trailing = {
                                DocMenu(
                                    onView = { onOpenViewer(doc.id) },
                                    onOpenWith = { context.openDocument(container, doc) },
                                    onShare = { context.shareDocument(container, doc) },
                                    onSave = { export(doc) },
                                    onRename = { renaming = doc },
                                    onMove = { moving = doc },
                                    onDelete = { deleting = doc },
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    renaming?.let { doc ->
        TextInputDialog(
            title = "Rename",
            initialValue = doc.name,
            label = "Name",
            onConfirm = { vm.rename(doc, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { doc ->
        ConfirmDialog(
            title = "Delete document?",
            message = "\"${doc.name}\" will be permanently removed from this device. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(doc); deleting = null },
            onDismiss = { deleting = null },
        )
    }

    moving?.let { doc ->
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text("Move to folder") },
            text = {
                if (folders.isEmpty()) {
                    Text("You have no folders yet. Create one from the Folders screen first.")
                } else {
                    Column {
                        folders.forEach { folder ->
                            TextButton(
                                onClick = { vm.move(doc, folder.id); moving = null },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(folder.name) }
                        }
                    }
                }
            },
            confirmButton = {
                if (doc.folderId != null) {
                    TextButton(onClick = { vm.move(doc, null); moving = null }) { Text("Remove from folder") }
                }
            },
            dismissButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DocMenu(
    onView: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("View") },
                leadingIcon = { Icon(Icons.Rounded.Visibility, null) },
                onClick = { expanded = false; onView() },
            )
            DropdownMenuItem(
                text = { Text("Open with…") },
                leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) },
                onClick = { expanded = false; onOpenWith() },
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                onClick = { expanded = false; onShare() },
            )
            DropdownMenuItem(
                text = { Text("Save to device") },
                leadingIcon = { Icon(Icons.Rounded.SaveAlt, null) },
                onClick = { expanded = false; onSave() },
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                onClick = { expanded = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text("Move to folder") },
                leadingIcon = { Icon(Icons.Rounded.DriveFileMove, null) },
                onClick = { expanded = false; onMove() },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}
