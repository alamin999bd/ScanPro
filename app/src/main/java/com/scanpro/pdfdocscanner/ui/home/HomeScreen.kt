package com.scanpro.pdfdocscanner.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UploadFile
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.ui.components.DocumentRow
import com.scanpro.pdfdocscanner.ui.components.containerViewModel
import com.scanpro.pdfdocscanner.ui.theme.Indigo500
import com.scanpro.pdfdocscanner.ui.theme.GlassSurface
import com.scanpro.pdfdocscanner.ui.theme.GlassOutline
import com.scanpro.pdfdocscanner.ui.theme.AccentPink
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
        Tile("PDF Tools", "Merge, split & more", Icons.Rounded.Construction, TilePdfTools, onPdfTools),
        Tile("Image to PDF", "Create PDF from photos", Icons.Rounded.Image, TileImageToPdf, onImageToPdf),
        Tile("ID Scan", "Scan identity documents", Icons.Rounded.Badge, TileIdScan, onIdScan),
        Tile("Import Files", "PDFs and images", Icons.Rounded.UploadFile, TileImport, onImport),
        Tile("My Documents", "Browse saved files", Icons.Rounded.Description, TileDocuments, onDocuments),
        Tile("Folders", "Organize your files", Icons.Rounded.FolderOpen, TileFolders, onFolders),
        Tile("Settings", "App preferences", Icons.Rounded.Settings, TileSettings, onSettings),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF17102F), Color(0xFF100D24), MaterialTheme.colorScheme.background)))
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { HomeHeader(onSearch) }
        item { ScanHero(count = count, onScan = onScanDocument) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Quick Tools",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onPdfTools) { Text("Explore") }
            }
        }
        items(tiles.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { tile -> FeatureTile(tile, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recent Documents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (count == 0) "Your scanned files will appear here" else "$count files in your library",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB8AECF),
                    )
                }
                if (recent.isNotEmpty()) TextButton(onClick = onDocuments) { Text("See all") }
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
    val context = LocalContext.current
    var online by remember { mutableStateOf(isInternetValidated(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = isInternetValidated(context) }
            override fun onLost(network: Network) { online = isInternetValidated(context) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        val request = android.net.NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { manager.registerNetworkCallback(request, callback) }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Document Scanner", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Scan, organize and manage PDFs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(GlassSurface)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (online) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = Color.White,
            )
            Spacer(Modifier.width(5.dp))
            Text(if (online) "Online" else "Offline", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, contentDescription = "Search documents")
        }
    }
}

@Composable
private fun ScanHero(count: Int, onScan: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF6738C8), Color(0xFF402080), Color(0xFF25204E))))
            .clickable(onClick = onScan)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("SMART DOCUMENT SCANNER", style = MaterialTheme.typography.labelMedium, color = Color(0xFFE1D5FF), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Scan Document", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(5.dp))
            Text(
                if (count == 0) "Turn paper into a clear PDF" else "$count documents saved on this device",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Start Scanning", color = Indigo900, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = Indigo900, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.Rounded.DocumentScanner,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(68.dp),
        )
    }
}

@Composable
private fun FeatureTile(tile: Tile, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = tile.onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(tile.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tile.icon, contentDescription = null, tint = tile.accent, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tile.title, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(tile.subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8AECF), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RecentEmptyState(onScan: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
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
            Text("Nothing scanned yet", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                "Scan a page or import a PDF and it will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onScan) { Text("Scan a document", color = Color(0xFFAC8CFF)) }
        }
    }
}


private fun isInternetValidated(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
