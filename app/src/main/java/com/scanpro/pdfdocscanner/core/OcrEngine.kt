package com.scanpro.pdfdocscanner.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Offline text recognition (OCR) with Tesseract, for Bangla and English.
 * The language data files are bundled inside the app (assets/tessdata), so nothing is downloaded
 * on the phone and the INTERNET permission is not needed.
 */
class OcrEngine(private val context: Context, private val pdf: PdfEngine) {

    class LanguageDataMissing : Exception(
        "The offline language data is not included in this build, so text recognition is unavailable."
    )

    private val mutex = Mutex()

    /** Reads all pages of [file] ([type] is "pdf" or "image") and returns the recognized text. */
    suspend fun extractText(file: File, type: String, onProgress: (Int, Int) -> Unit): String =
        mutex.withLock {
            withContext(Dispatchers.Default) {
                val base = prepareData()
                val api = TessBaseAPI()
                try {
                    if (!api.init(base.absolutePath, LANGUAGES)) {
                        throw IllegalStateException("The text recognizer could not start.")
                    }
                    val raw = if (type == "pdf") readPdf(api, file, onProgress) else readImage(api, file, onProgress)
                    tidy(raw)
                } finally {
                    api.recycle()
                }
            }
        }

    private fun prepareData(): File {
        val base = File(context.filesDir, "ocr")
        val tessdata = File(base, "tessdata")
        if (!tessdata.exists() && !tessdata.mkdirs()) throw IOException("Could not prepare the language data.")
        val available = context.assets.list("tessdata")?.toSet().orEmpty()
        for (lang in listOf("ben", "eng")) {
            val name = "$lang.traineddata"
            if (name !in available) throw LanguageDataMissing()
            val dest = File(tessdata, name)
            if (dest.exists() && dest.length() > 0) continue
            val tmp = File(tessdata, "$name.part")
            context.assets.open("tessdata/$name").use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                throw IOException("Could not prepare the language data.")
            }
        }
        return base
    }

    private suspend fun readPdf(api: TessBaseAPI, file: File, onProgress: (Int, Int) -> Unit): String {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(fd)
        } catch (e: Exception) {
            runCatching { fd.close() }
            throw e
        }
        try {
            val total = renderer.pageCount
            val out = StringBuilder()
            for (i in 0 until total) {
                currentCoroutineContext().ensureActive()
                onProgress(i + 1, total)
                val bitmap = renderer.openPage(i).use { page ->
                    val w = PAGE_WIDTH_PX
                    val h = (page.height.toFloat() * w / page.width).toInt().coerceIn(1, MAX_PAGE_HEIGHT_PX)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
                try {
                    api.setImage(bitmap)
                    out.append(api.getUTF8Text()).append("\n\n")
                } finally {
                    bitmap.recycle()
                }
            }
            return out.toString()
        } finally {
            runCatching { renderer.close() }
        }
    }

    private fun readImage(api: TessBaseAPI, file: File, onProgress: (Int, Int) -> Unit): String {
        onProgress(1, 1)
        val bitmap = pdf.decodeBitmap(Uri.fromFile(file), IMAGE_MAX_DIM)
            ?: throw IllegalStateException("The image could not be read.")
        return try {
            api.setImage(bitmap)
            api.getUTF8Text() ?: ""
        } finally {
            bitmap.recycle()
        }
    }

    private fun tidy(text: String): String =
        text.lines().joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    private companion object {
        const val LANGUAGES = "ben+eng"
        const val PAGE_WIDTH_PX = 1800
        const val MAX_PAGE_HEIGHT_PX = 3600
        const val IMAGE_MAX_DIM = 2400
    }
}
