package com.scanpro.pdfdocscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.scanpro.pdfdocscanner.data.AppSettings
import com.scanpro.pdfdocscanner.ui.ScanProRoot
import com.scanpro.pdfdocscanner.ui.theme.ScanProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ScanProApp).container
        setContent {
            val settings by container.settings.settings.collectAsState(initial = AppSettings())
            ScanProTheme(themeMode = settings.themeMode) {
                ScanProRoot(container)
            }
        }
    }
}
