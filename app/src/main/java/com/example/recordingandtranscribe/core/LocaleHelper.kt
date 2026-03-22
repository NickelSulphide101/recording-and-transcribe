package com.example.recordingandtranscribe.core

import android.app.LocaleManager
import android.content.Context

object LocaleHelper {
    /**
     * Determines whether the app should use Chinese localization.
     * Uses the modern Android 33+ LocaleManager API, retrieving the effective locales
     * prioritized by App Locales, then System Locales.
     * If not Chinese, defaults to the base English language.
     */
    fun isChinese(context: Context): Boolean {
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val appLocales = localeManager.applicationLocales
        val effectiveLocales = if (!appLocales.isEmpty) appLocales else localeManager.systemLocales
        
        return effectiveLocales.get(0)?.language?.lowercase()?.startsWith("zh") == true
    }
}

/**
 * Super clean inline extension for bilingual Compose string definitions.
 * Evaluates to the Chinese string if `LocaleHelper.isChinese(context)` is true, 
 * otherwise defaults to the receiver English string.
 */
fun String.zh(context: Context, chineseStr: String): String {
    return if (LocaleHelper.isChinese(context)) chineseStr else this
}
