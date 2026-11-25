package com.scanpro.pdfdocscanner

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ScanProApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
        // Scratch files from a previous session are never needed again.
        Thread { container.fileStore.clearTemp() }.start()
    }

    /**
     * Writes the details of the last crash to a small file on the phone (never sent anywhere), so
     * Settings > "Problem report" can show it and the user can copy it for the developer.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val text = buildString {
                    append("ScanPro ").append(BuildConfig.VERSION_NAME).append('\n')
                    append("Android ").append(android.os.Build.VERSION.RELEASE)
                        .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append("), ")
                        .append(android.os.Build.MODEL).append('\n')
                    append("Time ").append(java.util.Date()).append('\n')
                    append("Thread ").append(thread.name).append("\n\n")
                    append(android.util.Log.getStackTraceString(error).take(6000))
                }
                java.io.File(filesDir, CRASH_FILE).writeText(text)
            } catch (ignored: Throwable) {
                // Never let the logger itself hide the original crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
    }
}
