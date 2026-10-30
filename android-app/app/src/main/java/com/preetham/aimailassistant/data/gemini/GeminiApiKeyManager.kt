package com.preetham.aimailassistant.data.gemini

import com.preetham.aimailassistant.BuildConfig
import com.preetham.aimailassistant.security.AccountStorage

class GeminiApiKeyManager(private val accountStorage: AccountStorage) {

    fun getApiKey(): String {
        val storedKey = accountStorage.getGeminiApiKey()
        if (!storedKey.isNullOrBlank()) {
            return storedKey.trim()
        }
        if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            return BuildConfig.GEMINI_API_KEY.trim()
        }
        return ""
    }

    fun setApiKey(key: String?) {
        accountStorage.setGeminiApiKey(key?.trim()?.takeIf { it.isNotBlank() })
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()
}
