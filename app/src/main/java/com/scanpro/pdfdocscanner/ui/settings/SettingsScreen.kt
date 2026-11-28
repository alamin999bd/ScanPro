package com.scanpro.pdfdocscanner.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.scanpro.pdfdocscanner.core.FileStore
import com.scanpro.pdfdocscanner.data.DocumentRepository
import com.scanpro.pdfdocscanner.ui.components.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import com.scanpro.pdfdocscanner.ui.components.toast
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.BuildConfig
import com.scanpro.pdfdocscanner.core.PageSizes
import com.scanpro.pdfdocscanner.data.AppSettings
import com.scanpro.pdfdocscanner.data.SettingsRepository
import com.scanpro.pdfdocscanner.ui.components.ScanProTopBar
import com.scanpro.pdfdocscanner.ui.components.containerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Document count and total size in bytes. */
data class StorageUsage(val count: Int = 0, val bytes: Long = 0)

class SettingsViewModel(
    private val repo: SettingsRepository,
    docs: DocumentRepository,
    private val files: FileStore,
) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val usage: StateFlow<StorageUsage> =
        docs.observeDocuments()
            .map { list -> StorageUsage(list.size, list.sumOf { it.sizeBytes }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageUsage())

    private val _tempBytes = MutableStateFlow(files.tempSize())
    val tempBytes: StateFlow<Long> = _tempBytes

    fun clearTemp() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { files.clearTemp() }
            _tempBytes.value = files.tempSize()
        }
    }

    fun setTheme(mode: Int) { viewModelScope.launch { repo.setThemeMode(mode) } }
    fun setPageSize(size: Int) { viewModelScope.launch { repo.setPageSize(size) } }
    fun setQuality(q: Int) { viewModelScope.launch { repo.setImageQuality(q) } }
}

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm = containerViewModel(container) { SettingsViewModel(it.settings, it.documents, it.fileStore) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val tempBytes by vm.tempBytes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScanProTopBar(title = "Settings", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard(title = "Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip("System", Icons.Rounded.SettingsBrightness, settings.themeMode == 0) { vm.setTheme(0) }
                    ThemeChip("Light", Icons.Rounded.LightMode, settings.themeMode == 1) { vm.setTheme(1) }
                    ThemeChip("Dark", Icons.Rounded.DarkMode, settings.themeMode == 2) { vm.setTheme(2) }
                }
            }

            GeminiSecurityCard(context)

            SettingsCard(title = "PDF page size") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.pageSize == PageSizes.A4,
                        onClick = { vm.setPageSize(PageSizes.A4) },
                        label = { Text("A4") },
                    )
                    FilterChip(
                        selected = settings.pageSize == PageSizes.LETTER,
                        onClick = { vm.setPageSize(PageSizes.LETTER) },
                        label = { Text("US Letter") },
                    )
                    FilterChip(
                        selected = settings.pageSize == PageSizes.FIT_IMAGE,
                        onClick = { vm.setPageSize(PageSizes.FIT_IMAGE) },
                        label = { Text("Fit image") },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Used when creating PDFs from scans and images.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard(title = "Image quality in PDFs") {
                var sliderValue by remember(settings.imageQuality) {
                    mutableFloatStateOf(settings.imageQuality.toFloat())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { vm.setQuality(sliderValue.toInt()) },
                        valueRange = 60f..100f,
                        steps = 7,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("${sliderValue.toInt()}%", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    "Higher quality looks sharper but makes larger files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard(title = "Storage") {
                Text(
                    if (usage.count == 1) "1 document · ${formatFileSize(usage.bytes)}"
                    else "${usage.count} documents · ${formatFileSize(usage.bytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Stored privately inside the app. Use \"Save to device\" on a document to keep a copy elsewhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = vm::clearTemp, enabled = tempBytes > 0) {
                    Text(
                        if (tempBytes > 0) "Clear temporary files (${formatFileSize(tempBytes)})"
                        else "No temporary files"
                    )
                }
                Text(
                    "Only scratch files are cleared. Your documents are never deleted here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SettingsCard(title = "Permissions") {
                Text(
                    "Camera: ${if (cameraGranted) "allowed" else "not allowed"}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Used only to capture pages. Importing files and choosing photos uses the system pickers, " +
                        "so no storage permission is needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }) { Text("Open app permissions") }
            }

            ProblemReportCard()

            SettingsCard(title = "About") {
                Text("Document Scanner", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Core scanning and PDF tools work offline. Gemini AI features need internet and send the selected PDF text to Google only when you choose an AI action. " +
                        "No account or ads. Avoid sending private or sensitive documents to Gemini.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Shows the details of the last crash (kept only on this phone) so it can be copied for the developer. */
@Composable
private fun ProblemReportCard() {
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val file = remember { java.io.File(context.filesDir, com.scanpro.pdfdocscanner.ScanProApp.CRASH_FILE) }
    var report by remember { mutableStateOf(runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")) }

    SettingsCard(title = "Problem report") {
        if (report.isBlank()) {
            Text(
                "No crash has been recorded. If the app ever closes by itself, open this screen again " +
                    "and copy the report to send to the developer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "The app closed unexpectedly last time. Copy this report and send it to the developer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    report.take(1500),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(report))
                    context.toast("Report copied")
                }) { Text("Copy report") }
                OutlinedButton(onClick = {
                    runCatching { file.delete() }
                    report = ""
                }) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun ThemeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}
