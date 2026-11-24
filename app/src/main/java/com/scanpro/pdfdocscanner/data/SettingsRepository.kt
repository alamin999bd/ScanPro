package com.scanpro.pdfdocscanner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    /** 0 = system, 1 = light, 2 = dark */
    val themeMode: Int = 0,
    /** 0 = A4, 1 = US Letter, 2 = fit image */
    val pageSize: Int = 0,
    /** JPEG quality used inside generated PDFs: 60..100 */
    val imageQuality: Int = 85,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = intPreferencesKey("theme_mode")
        val PAGE_SIZE = intPreferencesKey("page_size")
        val QUALITY = intPreferencesKey("image_quality")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME] ?: 0,
            pageSize = p[Keys.PAGE_SIZE] ?: 0,
            imageQuality = p[Keys.QUALITY] ?: 85,
        )
    }

    suspend fun setThemeMode(mode: Int) = context.dataStore.edit { it[Keys.THEME] = mode }
    suspend fun setPageSize(size: Int) = context.dataStore.edit { it[Keys.PAGE_SIZE] = size }
    suspend fun setImageQuality(q: Int) = context.dataStore.edit { it[Keys.QUALITY] = q.coerceIn(60, 100) }
}
