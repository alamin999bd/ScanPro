package com.scanpro.pdfdocscanner.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.data.db.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

private fun Context.startSafely(intent: Intent, noAppMessage: String) {
    try {
        startActivity(intent)
    } catch (ignored: ActivityNotFoundException) {
        toast(noAppMessage)
    }
}

/** Opens the file in another installed app (external PDF/image viewer). */
fun Context.openDocument(container: AppContainer, doc: DocumentEntity) {
    val file = container.fileStore.docFile(doc.fileName)
    if (!file.exists()) {
        toast("File not found")
        return
    }
    startSafely(
        container.fileStore.openIntent(file, mimeFor(doc), "Open ${doc.name}"),
        "No app found to open this file",
    )
}

fun Context.shareDocument(container: AppContainer, doc: DocumentEntity) {
    val file = container.fileStore.docFile(doc.fileName)
    if (!file.exists()) {
        toast("File not found")
        return
    }
    startSafely(
        container.fileStore.shareIntent(listOf(file), mimeFor(doc), "Share ${doc.name}"),
        "No app found to share with",
    )
}

private fun extensionFor(doc: DocumentEntity): String = when {
    doc.type == "pdf" -> ".pdf"
    doc.fileName.endsWith(".png", ignoreCase = true) -> ".png"
    doc.fileName.endsWith(".webp", ignoreCase = true) -> ".webp"
    else -> ".jpg"
}

/**
 * Returns a function that lets the user save a copy of a document anywhere on the phone
 * (Downloads, Documents, SD card, cloud-storage apps) using the system "Save as" screen.
 * This needs no storage permission and never touches the in-app original.
 */
@Composable
fun rememberDocExporter(container: AppContainer): (DocumentEntity) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Only the id is remembered, and it survives the app being recreated while the system "Save as"
    // screen is open (this happens on phones with little free memory).
    var pendingId by rememberSaveable { mutableStateOf<Long?>(null) }

    val onResult: (Uri?) -> Unit = { uri ->
        val id = pendingId
        pendingId = null
        if (uri != null && id != null) {
            scope.launch {
                val ok = runCatching {
                    withContext(Dispatchers.IO) {
                        val doc = container.documents.getDocument(id) ?: throw IllegalStateException("missing")
                        container.fileStore.exportTo(container.documents.fileOf(doc), uri)
                    }
                }.isSuccess
                context.toast(if (ok) "Saved to your device" else "Could not save the file")
            }
        }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), onResult)
    val jpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg"), onResult)
    val png = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png"), onResult)
    val webp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/webp"), onResult)

    return { doc ->
        pendingId = doc.id
        val name = doc.name + extensionFor(doc)
        try {
            when (mimeFor(doc)) {
                "application/pdf" -> pdf.launch(name)
                "image/png" -> png.launch(name)
                "image/webp" -> webp.launch(name)
                else -> jpeg.launch(name)
            }
        } catch (ignored: ActivityNotFoundException) {
            // No "Save as" screen on this phone: save straight into Downloads/ScanPro instead.
            pendingId = null
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        container.fileStore.saveToDownloads(container.documents.fileOf(doc), name, mimeFor(doc))
                    }
                }
                when {
                    result.isFailure -> context.toast("Could not save the file")
                    result.getOrNull() == true -> context.toast("Saved to Downloads/ScanPro")
                    else -> context.toast("Saving is not available on this phone. Use Share instead.")
                }
            }
        }
    }
}
