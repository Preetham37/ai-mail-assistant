package com.preetham.aimailassistant.data.gemini

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.preetham.aimailassistant.data.AccountMeta
import com.preetham.aimailassistant.data.BriefingData
import com.preetham.aimailassistant.data.MorningBriefing
import com.preetham.aimailassistant.data.RawEmail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class GeminiSummarizer(
    private val apiKeyManager: GeminiApiKeyManager,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "GeminiSummarizer"
        val MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite")
    }

    suspend fun summarize(
        emails: List<RawEmail>,
        accounts: List<AccountMeta>,
        mode: String = "auto"
    ): Result<BriefingData> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyManager.getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API Key not configured. Please configure it in Settings."))
        }

        val resolvedMode = if (mode == "auto") {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour < 14) "morning" else "evening"
        } else {
            mode.lowercase()
        }

        // If no emails found across all inboxes, return an empty snapshot immediately
        if (emails.isEmpty()) {
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            return@withContext Result.success(
                BriefingData(
                    briefing_type = resolvedMode,
                    generated_at = nowIso,
                    accounts = accounts,
                    morning_briefing = MorningBriefing(),
                    email_details = emptyMap()
                )
            )
        }

        val prompt = GeminiPromptBuilder.buildPrompt(emails, resolvedMode)
        var lastError: Exception? = null

        for (model in MODELS) {
            try {
                Log.i(TAG, "Requesting AI synthesis using model: $model [Mode: $resolvedMode]...")
                val responseJsonStr = executeGeminiGenerateContent(model, apiKey, prompt)
                val briefingData = ContractV2PostProcessor.process(
                    rawAiJson = responseJsonStr,
                    emails = emails,
                    accounts = accounts,
                    mode = resolvedMode
                )
                return@withContext Result.success(briefingData)
            } catch (e: Exception) {
                Log.w(TAG, "Model $model failed: ${e.message}. Trying fallback model...")
                lastError = e
            }
        }

        Result.failure(lastError ?: RuntimeException("All Gemini models failed to respond."))
    }

    fun executeGeminiGenerateContent(model: String, apiKey: String, prompt: String): String {
        val url = "$baseUrl/models/$model:generateContent?key=$apiKey"

        val partObj = JsonObject().apply { addProperty("text", prompt) }
        val partsArray = JsonArray().apply { add(partObj) }
        val contentsObj = JsonObject().apply {
            addProperty("role", "user")
            add("parts", partsArray)
        }
        val genConfigObj = JsonObject().apply {
            addProperty("responseMimeType", "application/json")
            addProperty("temperature", 0.2)
        }
        val requestPayload = JsonObject().apply {
            add("contents", JsonArray().apply { add(contentsObj) })
            add("generationConfig", genConfigObj)
        }

        val request = Request.Builder()
            .url(url)
            .post(requestPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("Empty response from Gemini API")
            if (!response.isSuccessful) {
                throw IOException("Gemini API HTTP ${response.code}: $body")
            }

            val respObj = JsonParser.parseString(body).asJsonObject
            val candidates = respObj.getAsJsonArray("candidates") ?: throw IOException("No candidates in Gemini response")
            if (candidates.size() == 0) throw IOException("Zero candidates returned by Gemini")

            val parts = candidates.get(0).asJsonObject
                .getAsJsonObject("content")
                .getAsJsonArray("parts")

            return parts.get(0).asJsonObject.get("text").asString
        }
    }
}
