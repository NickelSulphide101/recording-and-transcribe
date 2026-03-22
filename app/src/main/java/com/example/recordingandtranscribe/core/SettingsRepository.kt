package com.example.recordingandtranscribe.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    companion object {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL_NAME = stringPreferencesKey("gemini_model_name")
    }

    val apiKeyFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[GEMINI_API_KEY]
        }

    val modelNameFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[GEMINI_MODEL_NAME]
        }

    suspend fun saveSettings(apiKey: String, modelName: String) {
        context.dataStore.edit { settings ->
            settings[GEMINI_API_KEY] = apiKey
            settings[GEMINI_MODEL_NAME] = modelName
        }
    }
}
