package com.scanpro.pdfdocscanner

import android.content.Context
import com.scanpro.pdfdocscanner.core.FileStore
import com.scanpro.pdfdocscanner.core.ImageProcessor
import com.scanpro.pdfdocscanner.core.OfficeConverter
import com.scanpro.pdfdocscanner.core.PdfEngine
import com.scanpro.pdfdocscanner.data.DocumentRepository
import com.scanpro.pdfdocscanner.data.SettingsRepository
import com.scanpro.pdfdocscanner.data.db.AppDatabase

/** Manual dependency container (no DI framework: keeps the build small and fully offline). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val fileStore: FileStore by lazy { FileStore(appContext) }
    val pdfEngine: PdfEngine by lazy { PdfEngine(appContext) }
    val officeConverter: OfficeConverter by lazy { OfficeConverter() }
    val imageProcessor: ImageProcessor by lazy { ImageProcessor(pdfEngine) }
    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val documents: DocumentRepository by lazy { DocumentRepository(database, fileStore) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
}
