package com.scanpro.pdfdocscanner.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.ui.components.DocumentRow
import com.scanpro.pdfdocscanner.ui.components.containerViewModel
import com.scanpro.pdfdocscanner.ui.components.openDocument
import com.scanpro.pdfdocscanner.ui.theme.Indigo500
import com.scanpro.pdfdocscanner.ui.theme.Indigo700
import com.scanpro.pdfdocscanner.ui.theme.Indigo900
import com.scanpro.pdfdocscanner.ui.theme.TileDocuments
import com.scanpro.pdfdocscanner.ui.theme.TileFolders
import com.scanpro.pdfdocscanner.ui.theme.TileIdScan
import com.scanpro.pdfdocscanner.ui.theme.TileImageToPdf
import com.scanpro.pdfdocscanner.ui.theme.TileImport
import com.scanpro.pdfdocscanner.ui.theme.TilePdfTools
import com.scanpro.pdfdocscanner.ui.theme.TileScan
import com.scanpro.pdfdocscanner.ui.theme.TileSettings

private data class Tile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit,
)

@Composable
fun HomeScreen(
    container: AppContainer,
    onScanDocument: () -> Unit,
    onImageToPdf: () -> Unit,
    onPdfTools: () -> Unit,
    onIdScan: () -> Unit,
    onImport: () -> Unit,
    onDocuments: () -> Unit,
    onSearch: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onFolders: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm = containerViewModel(container) { HomeViewModel(it.documents) }
    val count by vm.documentCount.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()

    val tiles = listOf(
        Tile("Document Scan", "Camera to PDF", Icons.Rounded.DocumentScanner, TileScan, onScanDocument),
        Tile("Image to PDF", "Photos into one PDF", Icons.Rounded.Image, TileImageToPdf, onImageToPdf),
        Tile("PDF Tools", "Merge, split, export", Icons.Rounded.Construction, TilePdfTools, onPdfTools),
        Tile("ID Scan", "Front & back on one page", Icons.Rounded.Badge, TileIdScan, onIdScan),
        Tile("Import", "Bring in PDFs & images", Icons.Rounded.UploadFile, TileImport, onImport),
        Tile("My Documents", "All your files", Icons.Rounded.Description, TileDocuments, onDocuments),
        Tile("Folders", "Organize documents", Icons.Rounded.FolderOpen, TileFolders, onFolders),
        Tile("Settings", "Theme & PDF quality", Icons.Rounded.Settings, TileSettings, onSettings),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HomeHeader(onSearch) }
        item { SummaryBanner(count = count, onOpenDocuments = onDocuments) }
        items(tiles.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { tile -> FeatureTile(tile, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Recent",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (recent.isNotEmpty()) {
                    TextButton(onClick = onDocuments) { Text("See all") }
                }
            }
        }
        if (recent.isEmpty()) {
            item { RecentEmptyState(onScanDocument) }
        } else {
            items(recent, key = { it.id }) { doc ->
                DocumentRow(
                    doc = doc,
                    container = container,
                    onClick = { onOpenDocument(doc.id) },
                )
            }
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun HomeHeader(onSearch: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Document Scanner", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Scan, OCR & PDF Tools",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Offline",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, contentDescription = "Search documents")
        }
    }
}

@Composable
private fun SummaryBanner(count: Int, onOpenDocuments: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Indigo500, Indigo700, Indigo900)))
            .clickable(onClick = onOpenDocuments)
            .padding(22.dp),
    ) {
        Column {
            Text(
                if (count == 0) "Your library is empty" else if (count == 1) "1 document" else "$count documents",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Everything stays on this device. No account, no internet, no uploads.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun FeatureTile(tile: Tile, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = tile.onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tile.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tile.icon, contentDescription = null, tint = tile.accent, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                tile.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                tile.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentEmptyState(onScan: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.DocumentScanner,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text("Nothing scanned yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Scan a page or import a PDF and it will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onScan) { Text("Scan a document") }
        }
    }
}
