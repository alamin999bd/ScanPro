package com.scanpro.pdfdocscanner.ui.pdftools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrandingWatermark
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.data.db.DocumentEntity
import com.scanpro.pdfdocscanner.ui.components.DocumentRow
import com.scanpro.pdfdocscanner.ui.components.EmptyState
import com.scanpro.pdfdocscanner.ui.components.ProgressDialog
import com.scanpro.pdfdocscanner.ui.components.ScanProTopBar
import com.scanpro.pdfdocscanner.ui.components.containerViewModel
import com.scanpro.pdfdocscanner.ui.theme.TileDocuments
import com.scanpro.pdfdocscanner.ui.theme.TileFolders
import com.scanpro.pdfdocscanner.ui.theme.TileIdScan
import com.scanpro.pdfdocscanner.ui.theme.TileImageToPdf
import com.scanpro.pdfdocscanner.ui.theme.TileImport
import com.scanpro.pdfdocscanner.ui.theme.TilePdfTools
import com.scanpro.pdfdocscanner.ui.theme.TileScan

private enum class Tool(val title: String, val description: String, val icon: ImageVector, val accent: Color) {
    MERGE("Merge PDFs", "Combine two or more PDFs into one file.", Icons.Rounded.MergeType, TileScan),
    EXTRACT("Split / Extract pages", "Pull chosen pages out into a new PDF.", Icons.Rounded.ContentCut, TilePdfTools),
    TO_IMAGES("PDF to Images", "Save every page of a PDF as a JPEG image.", Icons.Rounded.Image, TileImageToPdf),
    COMPRESS("Compress PDF", "Make a smaller copy that is easier to share.", Icons.Rounded.Compress, TileImport),
    LOCK("Lock with password", "Save a copy that needs a password to open.", Icons.Rounded.Lock, TileIdScan),
    UNLOCK("Remove password", "Save an unprotected copy (you must know the password).", Icons.Rounded.LockOpen, TileFolders),
    WATERMARK("Add watermark", "Stamp a text across every page.", Icons.Rounded.BrandingWatermark, TileDocuments),
    SIGN("Sign PDF", "Draw your signature and place it on a page.", Icons.Rounded.Edit, TileScan),
}

@Composable
fun PdfToolsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm = containerViewModel(container) { PdfToolsViewModel(it.documents, it.fileStore, it.pdfEngine) }
    val pdfs by vm.pdfs.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    var tool by remember { mutableStateOf<Tool?>(null) }
    BackHandler(enabled = tool != null) { tool = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScanProTopBar(
                title = tool?.title ?: "PDF Tools",
                onBack = { if (tool != null) tool = null else onBack() },
            )
        },
    ) { padding ->
        val list = pdfs
        val current = tool
        when {
            list == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> EmptyState(
                icon = Icons.Rounded.PictureAsPdf,
                title = "No PDFs yet",
                message = "Scan a document, convert images, or import a PDF first. Then come back to merge or split it.",
                modifier = Modifier.padding(padding),
            )
            current == null -> ToolMenu(Modifier.padding(padding)) { tool = it }
            else -> ToolPanel(
                tool = current,
                pdfs = list,
                container = container,
                modifier = Modifier.padding(padding),
                onMerge = vm::merge,
                onExtract = vm::extract,
                onToImages = vm::toImages,
                onCompress = vm::compress,
                onLock = vm::lock,
                onUnlock = vm::unlock,
                onWatermark = vm::watermark,
                onSign = vm::sign,
            )
        }
    }

    busy?.let { ProgressDialog(message = it) }
    message?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::dismissMessage,
            title = { Text(if (msg.isError) "Something went wrong" else "Done") },
            text = { Text(msg.text) },
            confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK") } },
        )
    }
}

@Composable
private fun ToolMenu(modifier: Modifier, onPick: (Tool) -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tool.entries.forEach { tool ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onPick(tool) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .padding(0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(tool.icon, contentDescription = null, tint = tool.accent, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tool.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            tool.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolPanel(
    tool: Tool,
    pdfs: List<DocumentEntity>,
    container: AppContainer,
    modifier: Modifier,
    onMerge: (List<DocumentEntity>, String) -> Unit,
    onExtract: (DocumentEntity, String, String) -> Unit,
    onToImages: (DocumentEntity) -> Unit,
    onCompress: (DocumentEntity, Int, String) -> Unit,
    onLock: (DocumentEntity, String, String) -> Unit,
    onUnlock: (DocumentEntity, String, String) -> Unit,
    onWatermark: (DocumentEntity, String, String) -> Unit,
    onSign: (DocumentEntity, Bitmap, String, Int, String) -> Unit,
) {
    // Selection order matters for merging, so keep an ordered list.
    val selected = remember(tool) { mutableStateListOf<DocumentEntity>() }
    var pages by remember(tool) { mutableStateOf("") }
    var name by remember(tool) { mutableStateOf("") }
    var password by remember(tool) { mutableStateOf("") }
    var watermarkText by remember(tool) { mutableStateOf("") }
    var level by remember(tool) { mutableIntStateOf(1) }
    var strokes by remember(tool) { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var signPage by remember(tool) { mutableStateOf("") }
    var signPos by remember(tool) { mutableIntStateOf(2) }
    val multi = tool == Tool.MERGE

    val hint = when (tool) {
        Tool.MERGE -> "Tap PDFs in the order you want them joined."
        Tool.EXTRACT -> "Choose one PDF, then enter the pages to keep."
        Tool.TO_IMAGES -> "Choose the PDF to export as images."
        Tool.COMPRESS -> "Choose one PDF. The original is kept; a smaller copy is added."
        Tool.LOCK -> "Choose one PDF. A password-protected copy is added; the original is kept."
        Tool.UNLOCK -> "Choose a password-protected PDF and enter its password."
        Tool.WATERMARK -> "Choose one PDF. A watermarked copy is added; the original is kept."
        Tool.SIGN -> "Choose one PDF. A signed copy is added; the original is kept."
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(pdfs, key = { it.id }) { doc ->
                val order = selected.indexOfFirst { it.id == doc.id }
                val isSelected = order >= 0
                val toggle = {
                    if (multi) {
                        if (isSelected) selected.removeAt(order) else selected.add(doc)
                    } else {
                        selected.clear()
                        selected.add(doc)
                    }
                }
                DocumentRow(
                    doc = doc,
                    container = container,
                    onClick = { toggle() },
                    trailing = {
                        if (multi) {
                            if (isSelected) {
                                Text("${order + 1}", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                            }
                            Checkbox(checked = isSelected, onCheckedChange = { toggle() })
                        } else {
                            RadioButton(selected = isSelected, onClick = { toggle() })
                        }
                    },
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tool == Tool.EXTRACT) {
                val total = selected.firstOrNull()?.pageCount?.takeIf { it > 0 }
                OutlinedTextField(
                    value = pages,
                    onValueChange = { pages = it },
                    label = { Text(if (total != null) "Pages (1-$total), e.g. 1-3, 5" else "Pages, e.g. 1-3, 5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (tool == Tool.COMPRESS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Smaller", "Balanced", "Better quality").forEachIndexed { i, label ->
                        FilterChip(selected = level == i, onClick = { level = i }, label = { Text(label) })
                    }
                }
                Text(
                    "Pages are re-drawn as pictures, so text in the copy can no longer be selected or searched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tool == Tool.LOCK || tool == Tool.UNLOCK) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (tool == Tool.LOCK) "Password (at least 4 characters)" else "Current password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tool == Tool.LOCK) {
                    Text(
                        "Remember this password: it cannot be recovered. ScanPro cannot preview a locked PDF, " +
                            "use \"Remove password\" to open it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (tool == Tool.SIGN) {
                SignaturePad(
                    strokes = strokes,
                    onChange = { strokes = it },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                )
                TextButton(onClick = { strokes = emptyList() }, enabled = strokes.isNotEmpty()) { Text("Clear signature") }
                OutlinedTextField(
                    value = signPage,
                    onValueChange = { v -> signPage = v.filter { it.isDigit() }.take(5) },
                    label = { Text("Page number (empty = last page)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Bottom left", "Bottom centre", "Bottom right").forEachIndexed { i, label ->
                        FilterChip(selected = signPos == i, onClick = { signPos = i }, label = { Text(label) })
                    }
                }
                Text(
                    "This places a picture of your signature. It is not a certified digital signature.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tool == Tool.WATERMARK) {
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("Watermark text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (tool != Tool.TO_IMAGES) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("New file name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val ready = when (tool) {
                Tool.MERGE -> selected.size >= 2
                Tool.EXTRACT -> selected.size == 1 && pages.isNotBlank()
                Tool.TO_IMAGES -> selected.size == 1
                Tool.COMPRESS -> selected.size == 1
                Tool.LOCK -> selected.size == 1 && password.length >= 4
                Tool.UNLOCK -> selected.size == 1 && password.isNotEmpty()
                Tool.WATERMARK -> selected.size == 1 && watermarkText.isNotBlank()
                Tool.SIGN -> selected.size == 1 && selected.first().pageCount > 0 && strokes.isNotEmpty()
            }
            Button(
                onClick = {
                    when (tool) {
                        Tool.MERGE -> onMerge(selected.toList(), name)
                        Tool.EXTRACT -> onExtract(selected.first(), pages, name)
                        Tool.TO_IMAGES -> onToImages(selected.first())
                        Tool.COMPRESS -> onCompress(selected.first(), level, name)
                        Tool.LOCK -> onLock(selected.first(), password, name)
                        Tool.UNLOCK -> onUnlock(selected.first(), password, name)
                        Tool.WATERMARK -> onWatermark(selected.first(), watermarkText.trim(), name)
                        Tool.SIGN -> {
                            val bitmap = renderSignature(strokes)
                            if (bitmap != null) onSign(selected.first(), bitmap, signPage, signPos, name)
                        }
                    }
                },
                enabled = ready,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    when (tool) {
                        Tool.MERGE -> if (selected.size >= 2) "Merge ${selected.size} PDFs" else "Select at least 2 PDFs"
                        Tool.EXTRACT -> "Extract pages"
                        Tool.TO_IMAGES -> "Export as images"
                        Tool.COMPRESS -> "Compress"
                        Tool.LOCK -> "Lock PDF"
                        Tool.UNLOCK -> "Remove password"
                        Tool.WATERMARK -> "Add watermark"
                        Tool.SIGN -> "Sign PDF"
                    }
                )
            }
        }
    }
}
