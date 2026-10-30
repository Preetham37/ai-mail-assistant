package com.preetham.aimailassistant.data.gemini

import com.preetham.aimailassistant.data.AccountMeta
import com.preetham.aimailassistant.data.RawEmail
import com.preetham.aimailassistant.security.InMemoryAccountStorage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeminiSummarizerTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var storage: InMemoryAccountStorage
    private lateinit var apiKeyManager: GeminiApiKeyManager
    private lateinit var summarizer: GeminiSummarizer

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        storage = InMemoryAccountStorage()
        storage.setGeminiApiKey("test_mock_api_key")
        apiKeyManager = GeminiApiKeyManager(storage)
        summarizer = GeminiSummarizer(
            apiKeyManager = apiKeyManager,
            baseUrl = mockServer.url("/v1beta").toString().removeSuffix("/")
        )
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `summarize returns empty snapshot when emails list is empty`() = runBlocking {
        val accounts = listOf(AccountMeta(id = "acc_1", label = "WORK"))
        val result = summarizer.summarize(emptyList(), accounts, mode = "morning")

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("morning", data.briefing_type)
        assertTrue(data.morning_briefing.important.isEmpty())
        assertTrue(data.email_details.isEmpty())
    }

    @Test
    fun `summarize falls back to secondary model when primary model fails`() = runBlocking {
        // Enqueue error 503 for first model (gemini-3.8-flash)
        mockServer.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))

        // Enqueue success for second model (gemini-3.5-flash)
        val geminiCandidateJson = """
            {
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {
                                    "text": "{\"morning_briefing\": {\"important\": [{\"id\": \"msg_01\", \"account_id\": \"acc_1\", \"account_label\": \"WORK\", \"account_color_hex\": \"#3B82F6\", \"summary\": \"System running normally\", \"action_required\": false}], \"needs_attention\": [], \"informational\": [], \"actions\": []}}"
                                }
                            ]
                        }
                    }
                ]
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(geminiCandidateJson))

        val email = RawEmail(
            id = "msg_01",
            accountId = "acc_1",
            accountLabel = "WORK",
            accountColorHex = "#3B82F6",
            subject = "Daily Report",
            body = "Everything is operational."
        )
        val accounts = listOf(AccountMeta(id = "acc_1", label = "WORK", color_hex = "#3B82F6"))

        val result = summarizer.summarize(listOf(email), accounts, mode = "morning")

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(1, data.morning_briefing.important.size)
        assertEquals("msg_01", data.morning_briefing.important.first().id)
        assertEquals("System running normally", data.morning_briefing.important.first().summary)

        // Verify two requests were made (fallback occurred)
        assertEquals(2, mockServer.requestCount)
    }
}
