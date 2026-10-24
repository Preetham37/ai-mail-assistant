package com.preetham.aimailassistant.data

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class BriefingRepository(private val context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("briefing_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_COMPLETED_ACTIONS = "completed_action_ids"
        private const val PREF_CUSTOM_SYNC_URL = "custom_sync_url"
        private const val PRIMARY_SYNC_URL = "http://127.0.0.1:8080/gmail/data.json"
        private const val EMULATOR_SYNC_URL = "http://10.0.2.2:8080/gmail/data.json"
        const val GITHUB_RAW_URL = "https://raw.githubusercontent.com/Preetham37/ai-mail-assistant/main/gmail/data.json"
    }

    suspend fun loadData(): BriefingData = withContext(Dispatchers.IO) {
        // Fast instant local cache/assets load first
        try {
            loadFromAssets()
        } catch (e: Exception) {
            BriefingData()
        }
    }

    suspend fun loadFromAssets(): BriefingData = withContext(Dispatchers.IO) {
        context.assets.open("data.json").use { stream ->
            val reader = InputStreamReader(stream)
            gson.fromJson(reader, BriefingData::class.java)
        }
    }

    suspend fun refreshFromServer(): Result<BriefingData> = withContext(Dispatchers.IO) {
        try {
            val customUrl = getCustomSyncUrl()
            val urlsToTry = buildList {
                if (!customUrl.isNullOrBlank()) {
                    add(customUrl)
                }
                add(PRIMARY_SYNC_URL)
                add(EMULATOR_SYNC_URL)
                add(GITHUB_RAW_URL)
            }

            var lastError: Exception? = null
            for (url in urlsToTry) {
                try {
                    val data = fetchFromServer(url)
                    return@withContext Result.success(data)
                } catch (e: Exception) {
                    lastError = e
                }
            }
            Result.failure(lastError ?: RuntimeException("Failed to sync from all sources"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchFromServer(urlString: String): BriefingData {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.requestMethod = "GET"

        if (conn.responseCode == 200) {
            conn.inputStream.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream))
                return gson.fromJson(reader, BriefingData::class.java)
            }
        } else {
            throw RuntimeException("HTTP Error: ${conn.responseCode}")
        }
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
}
