package com.vibecode.simplerecorder.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore

private val Context.recorderPreferences by preferencesDataStore("recorder_preferences")
private val NOTIFICATIONS_PROMPTED = booleanPreferencesKey("notifications_prompted")

class NotificationPreferences(context: Context) {
    private val store = context.applicationContext.recorderPreferences

    suspend fun shouldPrompt(): Boolean {
        // Persist the prompt decision once, including when the user declines.
        var shouldPrompt = false
        store.edit { preferences ->
            if (preferences[NOTIFICATIONS_PROMPTED] != true) {
                shouldPrompt = true
                preferences[NOTIFICATIONS_PROMPTED] = true
            }
        }
        return shouldPrompt
    }
}
