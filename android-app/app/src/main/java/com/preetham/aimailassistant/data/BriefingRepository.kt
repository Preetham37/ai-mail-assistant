package com.preetham.aimailassistant.data

import android.content.Context
import com.google.gson.Gson
import com.preetham.aimailassistant.security.SecureAccountStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class BriefingRepository(private val context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("briefing_prefs", Context.MODE_PRIVATE)
    val secureAccountStorage = SecureAccountStorage(context)

    companion object {
        private const val PREF_COMPLETED_ACTIONS = "completed_action_ids"
        private const val PREF_CUSTOM_SYNC_URL = "custom_sync_url"
        private const val PREF_LAST_INGESTED_TIMESTAMP = "last_ingested_generated_at"
        private const val PRIMARY_SYNC_URL = "http://127.0.0.1:8080/gmail/data.json"
        private const val EMULATOR_SYNC_URL = "http://10.0.2.2:8080/gmail/data.json"
        const val GITHUB_RAW_URL = "https://raw.githubusercontent.com/Preetham37/ai-mail-assistant/main/gmail/data.json"
        private const val CACHE_FILE_NAME = "cached_briefing.json"
    }

    suspend fun loadData(): BriefingData = withContext(Dispatchers.IO) {
        // 1. Try local disk cache from previous online sync
        val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
        if (cacheFile.exists()) {
            try {
                val cached = gson.fromJson(cacheFile.readText(), BriefingData::class.java)
                if (cached != null && (cached.email_details.isNotEmpty() || cached.accounts.isNotEmpty())) {
                    return@withContext enrichAccounts(cached)
                }
            } catch (e: Exception) {
                // Ignore and fall back to assets
            }
        }
        // 2. Fall back to bundled assets seed
        try {
            val fromAssets = loadFromAssets()
            enrichAccounts(fromAssets)
        } catch (e: Exception) {
            enrichAccounts(BriefingData())
        }
    }

    suspend fun loadFromAssets(): BriefingData = withContext(Dispatchers.IO) {
        context.assets.open("data.json").use { stream ->
            val reader = InputStreamReader(stream)
            gson.fromJson(reader, BriefingData::class.java)
        }
    }

    private fun saveToDiskCache(data: BriefingData) {
        try {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            cacheFile.writeText(gson.toJson(data))
        } catch (e: Exception) {
            // Ignore cache write error
        }
    }

    suspend fun refreshFromServer(): Result<BriefingData> = withContext(Dispatchers.IO) {
        try {
            val customUrl = getCustomSyncUrl()
            val urlsToTry = buildList {
                if (!customUrl.isNullOrBlank()) {
                    add(customUrl to 3000)
                }
                // Try localhost USB reverse port forward with fast timeout
                add(PRIMARY_SYNC_URL to 1500)
                // GitHub Raw as cloud fallback with cache buster
                add(GITHUB_RAW_URL to 5000)
                // Emulator loopback
                add(EMULATOR_SYNC_URL to 1500)
            }

            var lastError: Exception? = null
            for ((url, timeoutMs) in urlsToTry) {
                try {
                    val finalUrl = if (url.contains("githubusercontent")) {
                        "$url?nocache=${System.currentTimeMillis()}"
                    } else {
                        url
                    }
                    val data = fetchFromServer(finalUrl, timeoutMs)
                    val enriched = enrichAccounts(data)
                    saveToDiskCache(enriched)
                    return@withContext Result.success(enriched)
                } catch (e: Exception) {
                    lastError = e
                }
            }
            Result.failure(lastError ?: RuntimeException("Failed to sync from all sources"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchFromServer(urlString: String, timeoutMs: Int = 4000): BriefingData {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.requestMethod = "GET"
        conn.setRequestProperty("Cache-Control", "no-cache")
        conn.setRequestProperty("Pragma", "no-cache")

        if (conn.responseCode == 200) {
            conn.inputStream.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream))
                return gson.fromJson(reader, BriefingData::class.java)
            }
        } else {
            throw RuntimeException("HTTP Error: ${conn.responseCode}")
        }
    }

    /**
     * Ensures connected accounts metadata is synced between payload and secure storage.
     */
    fun enrichAccounts(data: BriefingData): BriefingData {
        val stored = secureAccountStorage.getAccounts()
        val mergedAccounts = if (data.accounts.isNotEmpty()) {
            data.accounts.forEach { secureAccountStorage.addOrUpdateAccount(it) }
            data.accounts
        } else if (stored.isNotEmpty()) {
            stored
        } else {
            val defaultPrimary = AccountMeta(
                id = "primary",
                email = "user@gmail.com",
                display_name = "Primary Account",
                label = "PERSONAL",
                color_hex = "#3B82F6",
                is_active = true
            )
            secureAccountStorage.addOrUpdateAccount(defaultPrimary)
            listOf(defaultPrimary)
        }
        return data.copy(accounts = mergedAccounts)
    }

    /**
     * Filter briefing feed by selected account ID.
     * When accountId is null or "ALL", returns the full unified buffer across all accounts.
     */
    fun filterByAccount(data: BriefingData, accountId: String?): BriefingData {
        if (accountId == null || accountId == "ALL" || accountId.isBlank()) {
            return data
        }

        val filteredImportant = data.morning_briefing.important.filter {
            it.account_id.equals(accountId, ignoreCase = true)
        }
        val filteredAttention = data.morning_briefing.needs_attention.filter {
            it.account_id.equals(accountId, ignoreCase = true)
        }
        val filteredActions = data.morning_briefing.actions.filter {
            it.account_id.equals(accountId, ignoreCase = true)
        }
        val filteredEmails = data.email_details.filter { (_, email) ->
            email.account_id.equals(accountId, ignoreCase = true)
        }
        val filteredTopics = data.morning_briefing.informational.filter { topic ->
            topic.account_id.equals(accountId, ignoreCase = true) ||
                topic.ids.any { filteredEmails.containsKey(it) }
        }

        return data.copy(
            morning_briefing = data.morning_briefing.copy(
                important = filteredImportant,
                needs_attention = filteredAttention,
                informational = filteredTopics,
                actions = filteredActions
            ),
            email_details = filteredEmails
        )
    }

    fun getConnectedAccounts(): List<AccountMeta> {
        return secureAccountStorage.getAccounts()
    }

    fun addAccount(account: AccountMeta, token: String? = null, refreshToken: String? = null) {
        secureAccountStorage.addOrUpdateAccount(account, token, refreshToken)
    }

    fun removeAccount(accountId: String): Boolean {
        return secureAccountStorage.removeAccount(accountId)
    }

    fun getSelectedAccountId(): String? {
        return secureAccountStorage.getSelectedAccountId()
    }

    fun setSelectedAccountId(accountId: String?) {
        secureAccountStorage.setSelectedAccountId(accountId)
    }

    fun getCustomSyncUrl(): String? {
        return prefs.getString(PREF_CUSTOM_SYNC_URL, null)
    }

    fun setCustomSyncUrl(url: String?) {
        prefs.edit().putString(PREF_CUSTOM_SYNC_URL, url).apply()
    }

    fun getCompletedActionIds(): Set<String> {
        return prefs.getStringSet(PREF_COMPLETED_ACTIONS, emptySet()) ?: emptySet()
    }

    fun toggleActionCompleted(id: String): Set<String> {
        val current = getCompletedActionIds().toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        prefs.edit().putStringSet(PREF_COMPLETED_ACTIONS, current).apply()
        return current
    }

    fun markAllActions(ids: Collection<String>, completed: Boolean): Set<String> {
        val current = getCompletedActionIds().toMutableSet()
        if (completed) {
            current.addAll(ids)
        } else {
            current.removeAll(ids.toSet())
        }
        prefs.edit().putStringSet(PREF_COMPLETED_ACTIONS, current).apply()
        return current
    }

    fun getNightMode(): Boolean? {
        return if (prefs.contains("night_mode")) {
            prefs.getBoolean("night_mode", false)
        } else {
            null
        }
    }

    fun setNightMode(enabled: Boolean) {
        prefs.edit().putBoolean("night_mode", enabled).apply()
    }

    fun getLastIngestedGeneratedAt(): String? {
        return prefs.getString(PREF_LAST_INGESTED_TIMESTAMP, null)
    }

    fun setLastIngestedGeneratedAt(timestamp: String) {
        prefs.edit().putString(PREF_LAST_INGESTED_TIMESTAMP, timestamp).apply()
    }

    fun isDataFreshForSlot(data: BriefingData): Boolean {
        if (data.generated_at.isBlank()) return false
        val lastIngested = getLastIngestedGeneratedAt()
        // If we already notified for this exact generation cycle, it's not fresh yet
        if (lastIngested != null && lastIngested == data.generated_at) {
            return false
        }
        return true
    }
}
