package com.vibecode.simplerecorder.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val countdownSeconds: Int = 0,
    val dynamicColor: Boolean = false,
)

private val Context.appPreferences by preferencesDataStore("app_preferences")
private val THEME_MODE = stringPreferencesKey("theme_mode")
private val COUNTDOWN_SECONDS = intPreferencesKey("countdown_seconds")
private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

class AppPreferences(context: Context) {
    private val store = context.applicationContext.appPreferences

    val settings: Flow<AppSettings> = store.data.map { preferences ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(preferences[THEME_MODE] ?: ThemeMode.SYSTEM.name) }
                .getOrDefault(ThemeMode.SYSTEM),
            countdownSeconds = (preferences[COUNTDOWN_SECONDS] ?: 0).takeIf { it in COUNTDOWN_OPTIONS } ?: 0,
            dynamicColor = preferences[DYNAMIC_COLOR] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setCountdownSeconds(seconds: Int) {
        store.edit { it[COUNTDOWN_SECONDS] = if (seconds in COUNTDOWN_OPTIONS) seconds else 0 }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = enabled }
    }

    companion object {
        val COUNTDOWN_OPTIONS = listOf(0, 3, 5, 10)
    }
}
