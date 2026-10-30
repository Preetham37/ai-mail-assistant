package com.preetham.aimailassistant.data.gmail

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.preetham.aimailassistant.data.AccountMeta
import com.preetham.aimailassistant.data.RawEmail
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GmailApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://gmail.googleapis.com/gmail/v1/users/me",
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "GmailApiService"
    }

    /**
     * Fetches recent emails for a specific account.
     * Bounded concurrency: at most 8 parallel HTTP requests to avoid quota exhaustion.
     */
    suspend fun fetchAccountEmails(
        account: AccountMeta,
        accessToken: String,
        query: String = "newer_than:1d",
        maxResults: Int = 20
    ): List<RawEmail> = withContext(Dispatchers.IO) {
        val messageIds = listMessageIds(accessToken, query, maxResults)
        if (messageIds.isEmpty()) return@withContext emptyList()

        val semaphore = Semaphore(8)
        val deferredList = messageIds.map { msgId ->
            async {
                semaphore.withPermit {
                    fetchSingleEmail(account, accessToken, msgId)
                }
            }
        }

        deferredList.awaitAll().filterNotNull()
    }

    /**
     * Calls GET /messages?q={query}&maxResults={maxResults}
     */
    fun listMessageIds(accessToken: String, query: String, maxResults: Int): List<String> {
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query.replace(" ", "%20")
        }
        val url = "$baseUrl/messages?q=$encodedQuery&maxResults=$maxResults"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401) throw UnauthorizedException("Access token expired for Gmail API")
                Log.w(TAG, "Failed to list messages: HTTP ${response.code}")
                return emptyList()
            }
            val body = response.body?.string() ?: return emptyList()
            val json = JsonParser.parseString(body).asJsonObject
            val messages = json.getAsJsonArray("messages") ?: return emptyList()
            return messages.mapNotNull { it.asJsonObject.get("id")?.asString }
        }
    }

    /**
     * Calls GET /messages/{id}?format=full and extracts headers, RFC-ID, and body.
     */
    fun fetchSingleEmail(account: AccountMeta, accessToken: String, messageId: String): RawEmail? {
        val url = "$baseUrl/messages/$messageId?format=full"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 401) throw UnauthorizedException("Access token expired for Gmail API message $messageId")
                    return null
                }
                val bodyStr = response.body?.string() ?: return null
                parseMessageJson(account, messageId, JsonParser.parseString(bodyStr).asJsonObject)
            }
        } catch (e: UnauthorizedException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching message $messageId: ${e.message}")
            null
        }
    }

    /**
     * Traverses MIME payload tree recursively to extract plain-text and HTML bodies.
     */
    fun parseMessageJson(account: AccountMeta, messageId: String, json: JsonObject): RawEmail {
        val payload = json.getAsJsonObject("payload") ?: JsonObject()
        val headersArray = payload.getAsJsonArray("headers")

        var sender = "Unknown Sender"
        var subject = "(No Subject)"
        var date = ""
        var rfcMessageId = ""

        if (headersArray != null) {
            for (elem in headersArray) {
                val header = elem.asJsonObject
                val name = header.get("name")?.asString ?: ""
                val value = header.get("value")?.asString ?: ""
                when {
                    name.equals("From", ignoreCase = true) -> sender = value
                    name.equals("Subject", ignoreCase = true) -> subject = value
                    name.equals("Date", ignoreCase = true) -> date = value
                    name.equals("Message-ID", ignoreCase = true) || name.equals("Message-Id", ignoreCase = true) -> rfcMessageId = value
                }
            }
        }

        val plainTexts = mutableListOf<String>()
        val htmlBodies = mutableListOf<String>()
        extractParts(payload, plainTexts, htmlBodies)

        val substantiveBody = EmailSanitizer.resolveSubstantiveBody(
            plainText = plainTexts.firstOrNull(),
            htmlBody = htmlBodies.firstOrNull()
        )

        return RawEmail(
            id = messageId,
            accountId = account.id,
            accountLabel = account.label,
            accountColorHex = account.color_hex,
            accountEmail = account.email,
            sender = sender,
            subject = subject,
            date = date,
            body = substantiveBody,
            rfcMessageId = rfcMessageId
        )
    }

    private fun extractParts(part: JsonObject, plainTexts: MutableList<String>, htmlBodies: MutableList<String>) {
        val mimeType = part.get("mimeType")?.asString ?: ""
        val bodyObj = part.getAsJsonObject("body")
        val dataBase64 = bodyObj?.get("data")?.asString

        if (!dataBase64.isNullOrBlank()) {
            val decoded = decodeBase64Url(dataBase64)
            if (mimeType.equals("text/plain", ignoreCase = true)) {
                plainTexts.add(decoded)
            } else if (mimeType.equals("text/html", ignoreCase = true)) {
                htmlBodies.add(decoded)
            }
        }

        val partsArray = part.getAsJsonArray("parts")
        if (partsArray != null) {
            for (elem in partsArray) {
                if (elem.isJsonObject) {
                    extractParts(elem.asJsonObject, plainTexts, htmlBodies)
                }
            }
        }
    }

    fun decodeBase64Url(data: String): String {
        return try {
            val cleanData = data.trim().replace('-', '+').replace('_', '/')
            val padded = when (cleanData.length % 4) {
                2 -> cleanData + "=="
                3 -> cleanData + "="
                else -> cleanData
            }
            val bytes = java.util.Base64.getDecoder().decode(padded)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                val bytes = android.util.Base64.decode(data, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                String(bytes, Charsets.UTF_8)
            } catch (ex: Exception) {
                ""
            }
        }
    }
}

class UnauthorizedException(message: String) : IOException(message)
