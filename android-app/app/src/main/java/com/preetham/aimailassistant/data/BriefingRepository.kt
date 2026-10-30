package com.preetham.aimailassistant.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.preetham.aimailassistant.data.gemini.GeminiApiKeyManager
import com.preetham.aimailassistant.data.gemini.GeminiSummarizer
import com.preetham.aimailassistant.data.gmail.EmailDeduplicator
import com.preetham.aimailassistant.data.gmail.GmailApiService
import com.preetham.aimailassistant.data.gmail.SyncWindowPolicy
import com.preetham.aimailassistant.security.OAuthManager
import com.preetham.aimailassistant.security.AccountStorage
import com.preetham.aimailassistant.security.InMemoryAccountStorage
import com.preetham.aimailassistant.security.SecureAccountStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class BriefingRepository(
    private val context: Context? = null,
    val secureAccountStorage: AccountStorage = context?.let { SecureAccountStorage(it) } ?: InMemoryAccountStorage()
) {
    private val gson = Gson()
    private val prefs by lazy {
        context?.getSharedPreferences("briefing_prefs", Context.MODE_PRIVATE)
            ?: error("Context is required for preferences")
    }


    companion object {
        private const val PREF_COMPLETED_ACTIONS = "completed_action_ids"
        private const val PREF_CUSTOM_SYNC_URL = "custom_sync_url"
        private const val PREF_LAST_INGESTED_TIMESTAMP = "last_ingested_generated_at"
        private const val PREF_LAST_SYNC_EPOCH = "last_sync_epoch_seconds"
        private const val PRIMARY_SYNC_URL = "http://127.0.0.1:8080/gmail/data.json"
        private const val EMULATOR_SYNC_URL = "http://10.0.2.2:8080/gmail/data.json"
        const val GITHUB_RAW_URL = "https://raw.githubusercontent.com/Preetham37/ai-mail-assistant/main/gmail/data.json"
        private const val CACHE_FILE_NAME = "cached_briefing.json"
    }

    suspend fun loadData(): BriefingData = withContext(Dispatchers.IO) {
        // 1. Try local disk cache from previous online sync
        val cacheFile = context?.filesDir?.let { File(it, CACHE_FILE_NAME) }
        if (cacheFile != null && cacheFile.exists()) {
            try {
                val cached = gson.fromJson(cacheFile.readText(), BriefingData::class.java)
                if (cached != null && (cached.email_details.isNotEmpty() || cached.morning_briefing.important.isNotEmpty() || cached.morning_briefing.actions.isNotEmpty())) {
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
        val ctx = context ?: error("Context is required to load assets")
        ctx.assets.open("data.json").use { stream ->
            val reader = InputStreamReader(stream)
            gson.fromJson(reader, BriefingData::class.java)
        }
    }

    private fun saveToDiskCache(data: BriefingData) {
        try {
            val cacheFile = context?.filesDir?.let { File(it, CACHE_FILE_NAME) }
            cacheFile?.writeText(gson.toJson(data))
        } catch (e: Exception) {
            // Ignore cache write error
        }
    }

    /**
     * Executes direct on-device email ingestion and Gemini synthesis:
     * 1. Silent token refresh via Knox StrongBox + Google OAuth
     * 2. Direct Gmail API fetch across all active accounts (bounded concurrency)
     * 3. Jsoup HTML sanitization and cross-account deduplication
     * 4. Gemini AI synthesis (Contract v2.0 JSON format)
     * 5. Enriches accounts, caches to disk, and updates last ingested timestamp.
     */
    fun getLastSyncEpoch(): Long? {
        val epoch = prefs.getLong(PREF_LAST_SYNC_EPOCH, -1L)
        return if (epoch > 0) epoch else null
    }

    fun setLastSyncEpoch(epoch: Long) {
        prefs.edit().putLong(PREF_LAST_SYNC_EPOCH, epoch).apply()
    }

    fun loadFromDiskCache(): BriefingData? {
        val cacheFile = context?.filesDir?.let { File(it, CACHE_FILE_NAME) }
        return if (cacheFile != null && cacheFile.exists()) {
            try {
                gson.fromJson(cacheFile.readText(), BriefingData::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    suspend fun syncOnDevice(
        mode: String = "auto",
        query: String = "auto",
        maxResultsPerAccount: Int = 25
    ): Result<BriefingData> = withContext(Dispatchers.IO) {
        try {
            val accounts = secureAccountStorage.getAccounts().filter { it.is_active }
            if (accounts.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No connected Google accounts found. Please connect an account."))
            }

            // Compute dynamic incremental window and briefing mode
            val resolvedQuery = if (query == "auto") {
                SyncWindowPolicy.calculateQuery(getLastSyncEpoch())
            } else {
                query
            }

            val resolvedMode = if (mode == "auto") {
                SyncWindowPolicy.resolveBriefingMode()
            } else {
                mode
            }

            Log.i("BriefingRepository", "Executing on-device sync: mode=$resolvedMode, query=$resolvedQuery")

            // 1. Ingest emails across all active accounts
            val allRawEmails = mutableListOf<RawEmail>()
            val ctx = context ?: error("Context required for on-device sync")
            val oauthManager = OAuthManager(ctx, secureAccountStorage)
            val gmailService = GmailApiService()

            var authenticatedAccounts = 0
            for (account in accounts) {
                try {
                    // Ensure fresh OAuth access token from Knox
                    val tokenResult = oauthManager.getFreshAccessToken(account.id)
                    if (tokenResult.isFailure) {
                        Log.w("BriefingRepository", "Skipping account ${account.label}: token refresh failed (${tokenResult.exceptionOrNull()?.message})")
                        continue
                    }
                    val token = tokenResult.getOrThrow()
                    authenticatedAccounts++

                    val accountEmails = gmailService.fetchAccountEmails(
                        account = account,
                        accessToken = token,
                        query = resolvedQuery,
                        maxResults = maxResultsPerAccount
                    )
                    allRawEmails.addAll(accountEmails)
                } catch (e: Exception) {
                    Log.e("BriefingRepository", "Error fetching emails for ${account.label}: ${e.message}")
                }
            }

            if (authenticatedAccounts == 0) {
                return@withContext Result.failure(
                    IllegalStateException("Authentication required: Please tap [ACC: ALL ▾] and connect your Google account.")
                )
            }

            // 2. Perform cross-account deduplication
            val (dedupedEmails, _) = EmailDeduplicator.deduplicate(allRawEmails)

            // 3. Synthesize briefing using Gemini
            val apiKeyManager = GeminiApiKeyManager(secureAccountStorage)
            val summarizer = GeminiSummarizer(apiKeyManager)
            val aiResult = summarizer.summarize(
                emails = dedupedEmails,
                accounts = accounts,
                mode = resolvedMode
            )

            if (aiResult.isFailure) {
                return@withContext aiResult
            }

            val briefingData = aiResult.getOrThrow()

            // Merge newly synthesized email details into historical vault archive (up to 100 entries)
            val currentCache = loadFromDiskCache()
            val mergedEmailDetails = buildMap {
                currentCache?.email_details?.let { putAll(it) }
                putAll(briefingData.email_details)
            }
            val finalBriefingData = briefingData.copy(
                email_details = mergedEmailDetails
            )
            val enriched = enrichAccounts(finalBriefingData)

            // 4. Save to disk cache and update last ingested timestamp
            saveToDiskCache(enriched)
            setLastIngestedGeneratedAt(enriched.generated_at)
            setLastSyncEpoch(System.currentTimeMillis() / 1000L)

            Result.success(enriched)
        } catch (e: Exception) {
            Log.e("BriefingRepository", "On-device sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun refreshFromServer(): Result<BriefingData> = withContext(Dispatchers.IO) {
        // Priority 1: Direct On-Device Engine if accounts are connected
        if (context != null && secureAccountStorage.getAccounts().any { it.is_active }) {
            val onDeviceResult = syncOnDevice()
            if (onDeviceResult.isSuccess) {
                return@withContext onDeviceResult
            } else {
                Log.w("BriefingRepository", "On-device sync failed: ${onDeviceResult.exceptionOrNull()?.message}. Falling back to server/GitHub...")
            }
        }
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
    /**
     * Ensures connected accounts metadata is synced between payload and secure storage.
     * Merges both sources so that accounts added locally in Knox storage are NEVER lost
     * when new data arrives from the server.
     */
    fun enrichAccounts(data: BriefingData): BriefingData {
        val stored = secureAccountStorage.getAccounts()
        val accountMap = stored.associateBy { it.id }.toMutableMap()

        // 1. Merge any incoming accounts from data.json into storage and the map
        data.accounts.forEach { incoming ->
            val matchKey = accountMap.entries.firstOrNull {
                it.key.equals(incoming.id, ignoreCase = true) ||
                (incoming.email.isNotBlank() && it.value.email.equals(incoming.email, ignoreCase = true))
            }?.key

            if (matchKey != null) {
                val current = accountMap[matchKey]!!
                val updated = current.copy(
                    email = if (incoming.email.isNotBlank()) incoming.email else current.email,
                    display_name = if (incoming.display_name.isNotBlank()) incoming.display_name else current.display_name,
                    label = if (incoming.label.isNotBlank()) incoming.label else current.label,
                    color_hex = if (incoming.color_hex.isNotBlank()) incoming.color_hex else current.color_hex,
                    is_active = incoming.is_active
                )
                accountMap[matchKey] = updated
                secureAccountStorage.addOrUpdateAccount(updated)
            } else {
                accountMap[incoming.id] = incoming
                secureAccountStorage.addOrUpdateAccount(incoming)
            }
        }

        // 2. Ensure we always retain all connected accounts
        val finalAccounts = if (accountMap.isNotEmpty()) {
            accountMap.values.toList()
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

        return data.copy(accounts = finalAccounts)
    }

    /**
     * Filter briefing feed by selected account ID.
     * When accountId is null or "ALL", returns the full unified buffer across all accounts.
     * Handles account ID and email matching so differing identifier conventions across
     * pipeline and on-device PKCE never drop data.
     */
    fun filterByAccount(data: BriefingData, accountId: String?): BriefingData {
        if (accountId == null || accountId == "ALL" || accountId.isBlank()) {
            return data
        }

        val selectedAccount = data.accounts.firstOrNull { it.id.equals(accountId, ignoreCase = true) }
        val targetEmail = selectedAccount?.email
        val targetId = selectedAccount?.id ?: accountId

        fun matches(itemAccountId: String, itemAccountEmail: String = ""): Boolean {
            if (itemAccountId.equals(targetId, ignoreCase = true)) return true
            if (targetId.equals("primary", ignoreCase = true) && itemAccountId.equals("default", ignoreCase = true)) return true
            if (targetId.equals("default", ignoreCase = true) && itemAccountId.equals("primary", ignoreCase = true)) return true
            if (!targetEmail.isNullOrBlank() && itemAccountEmail.isNotBlank() && itemAccountEmail.equals(targetEmail, ignoreCase = true)) return true
            return false
        }

        val filteredImportant = data.morning_briefing.important.filter {
            matches(it.account_id)
        }
        val filteredAttention = data.morning_briefing.needs_attention.filter {
            matches(it.account_id)
        }
        val filteredActions = data.morning_briefing.actions.filter {
            matches(it.account_id)
        }
        val filteredEmails = data.email_details.filter { (_, email) ->
            matches(email.account_id, email.account_email)
        }
        val filteredTopics = data.morning_briefing.informational.filter { topic ->
            matches(topic.account_id) || topic.ids.any { filteredEmails.containsKey(it) }
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

    fun getGeminiApiKey(): String? = secureAccountStorage.getGeminiApiKey()

    fun setGeminiApiKey(key: String?) = secureAccountStorage.setGeminiApiKey(key)

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
