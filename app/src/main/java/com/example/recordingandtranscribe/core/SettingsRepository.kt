package com.example.recordingandtranscribe.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    companion object {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL_NAME = stringPreferencesKey("gemini_model_name")
        val BITRATE = intPreferencesKey("bitrate")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
        val IS_DENOISING_ENABLED = booleanPreferencesKey("is_denoising_enabled")
        val USE_GEMINI_NANO = booleanPreferencesKey("use_gemini_nano")
        val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
    }

    val apiKeyFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[GEMINI_API_KEY]
        }

    val modelNameFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[GEMINI_MODEL_NAME]
        }

    val bitrateFlow: Flow<Int> = context.dataStore.data
        .map { it[BITRATE] ?: 16000 }

    val skipSilenceFlow: Flow<Boolean> = context.dataStore.data
        .map { it[SKIP_SILENCE] ?: false }

    val isBiometricEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { it[IS_BIOMETRIC_ENABLED] ?: false }

    val isDenoisingEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { it[IS_DENOISING_ENABLED] ?: false }

    val useGeminiNanoFlow: Flow<Boolean> = context.dataStore.data
        .map { it[USE_GEMINI_NANO] ?: false }

    val isOnboardingCompletedFlow: Flow<Boolean> = context.dataStore.data
        .map { it[IS_ONBOARDING_COMPLETED] ?: false }

    // [SECURITY WARNING] API Key and Model Name are stored in plaintext DataStore.
    // For production apps, use EncryptedSharedPreferences or Android Keystore.
    suspend fun saveSettings(
        apiKey: String? = null,
        modelName: String? = null,
        bitrate: Int? = null,
        skipSilence: Boolean? = null,
        isBiometricEnabled: Boolean? = null,
        isDenoisingEnabled: Boolean? = null,
        useGeminiNano: Boolean? = null
    ) {
        context.dataStore.edit { settings ->
            apiKey?.let { settings[GEMINI_API_KEY] = it }
            modelName?.let { settings[GEMINI_MODEL_NAME] = it }
            bitrate?.let { settings[BITRATE] = it }
            skipSilence?.let { settings[SKIP_SILENCE] = it }
            isBiometricEnabled?.let { settings[IS_BIOMETRIC_ENABLED] = it }
            isDenoisingEnabled?.let { settings[IS_DENOISING_ENABLED] = it }
            useGeminiNano?.let { settings[USE_GEMINI_NANO] = it }
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { settings ->
            settings[IS_ONBOARDING_COMPLETED] = completed
        }
    }
}
