package com.scanpro.pdfdocscanner.core

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Owns every file the app writes. All data stays in the app's private storage. */
class FileStore(private val context: Context) {

    val docsDir: File = File(context.filesDir, "documents").apply { mkdirs() }
    val tempDir: File = File(context.cacheDir, "temp").apply { mkdirs() }

    fun docFile(fileName: String): File = File(docsDir, fileName)

    /** Creates a unique, not-yet-existing file in the documents directory. */
    fun newDocFile(extension: String): File =
        File(docsDir, "${UUID.randomUUID()}.${extension.trimStart('.')}")

    fun newTempFile(extension: String): File =
        File(tempDir, "${UUID.randomUUID()}.${extension.trimStart('.')}")

    fun clearTemp() {
        tempDir.listFiles()?.forEach { it.delete() }
    }

    /** Total size of scratch files in bytes. */
    fun tempSize(): Long = tempDir.listFiles()?.sumOf { it.length() } ?: 0L

    fun displayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    fun mimeType(uri: Uri): String? {
        context.contentResolver.getType(uri)?.let { return it }
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext?.lowercase())
    }

    /** Copies any content/file Uri into [dest]. Returns the number of bytes written. */
    fun copyTo(uri: Uri, dest: File): Long {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw java.io.FileNotFoundException("Cannot open $uri")
        return input.use { ins -> dest.outputStream().use { out -> ins.copyTo(out) } }
    }

    /** Writes [src] to a user-chosen destination (from the system "Save as" picker). */
    fun exportTo(src: File, dest: Uri) {
        val out = context.contentResolver.openOutputStream(dest)
            ?: throw java.io.IOException("Cannot write to $dest")
        out.use { o -> src.inputStream().use { it.copyTo(o) } }
    }

    /**
     * Saves a copy straight into the phone's Downloads/ScanPro folder (Android 10 and newer, no
     * permission needed). Used when no file-manager screen is available for "Save as".
     * Returns false on older Android versions, where the caller should suggest Share instead.
     */
    fun saveToDownloads(src: File, displayName: String, mime: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_DOWNLOADS + "/ScanPro",
            )
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("Could not create the file in Downloads")
        try {
            val out = resolver.openOutputStream(uri) ?: throw java.io.IOException("Could not open the file")
            out.use { o -> src.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return true
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun openIntent(file: File, mime: String, title: String): Intent {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(file), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, title)
    }

    fun shareIntent(files: List<File>, mime: String, title: String): Intent {
        val uris = ArrayList(files.map { uriFor(it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        intent.type = mime
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("", uris[0]).also { clip ->
            for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
        }
        return Intent.createChooser(intent, title)
    }

    companion object {
        fun defaultScanName(prefix: String = "Scan"): String {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(Date())
            return "$prefix $stamp"
        }

        fun stripExtension(name: String): String = name.substringBeforeLast('.', name)

        /** Removes characters that are unsafe in file names shown/shared to other apps. */
        fun sanitizeName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "Document" }
    }
}
