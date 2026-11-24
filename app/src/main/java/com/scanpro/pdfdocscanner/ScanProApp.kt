package com.scanpro.pdfdocscanner

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ScanProApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
        // Scratch files from a previous session are never needed again.
        Thread { container.fileStore.clearTemp() }.start()
    }
}
