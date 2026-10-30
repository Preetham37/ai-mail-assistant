package com.preetham.aimailassistant.data

import com.preetham.aimailassistant.data.gemini.ContractV2PostProcessor
import com.preetham.aimailassistant.data.gemini.GeminiApiKeyManager
import com.preetham.aimailassistant.data.gemini.GeminiSummarizer
import com.preetham.aimailassistant.data.gmail.EmailDeduplicator
import com.preetham.aimailassistant.data.gmail.GmailApiService
import com.preetham.aimailassistant.security.InMemoryAccountStorage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64

class BriefingRepositoryOnDeviceTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var storage: InMemoryAccountStorage

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        storage = InMemoryAccountStorage()
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `test on device ingestion and gemini summarization pipeline`() = runBlocking {
        // Setup account in storage
        val account = AccountMeta(
            id = "acc_work",
            email = "dev@company.com",
            display_name = "Work Dev",
            label = "WORK",
            color_hex = "#3B82F6",
            is_active = true
        )
        storage.saveAccounts(listOf(account))
        storage.updateAccessToken("acc_work", "mock_access_token")
        storage.setGeminiApiKey("mock_gemini_api_key")

        // 1. Mock Gmail API list messages
        val listJson = """
            {
                "messages": [
                    { "id": "m_100", "threadId": "t_100" }
                ]
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(listJson))

        // 2. Mock Gmail API get message
        val bodyContent = "CRITICAL: Server CPU spike on worker-01. Please investigate immediately."
        val encodedBody = Base64.getUrlEncoder().withoutPadding().encodeToString(bodyContent.toByteArray())
        val messageJson = """
            {
                "id": "m_100",
                "payload": {
                    "mimeType": "text/plain",
                    "headers": [
                        { "name": "From", "value": "alerts@infra.com" },
                        { "name": "Subject", "value": "ALERT: High CPU Usage" },
                        { "name": "Date", "value": "2026-10-30" },
                        { "name": "Message-ID", "value": "<cpu-alert-100@infra.com>" }
                    ],
                    "body": {
                        "data": "$encodedBody"
                    }
                }
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(messageJson))

        // 3. Mock Gemini API generateContent response
        val geminiRespJson = """
            {
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                {
                                    "text": "{\"morning_briefing\": {\"important\": [{\"id\": \"m_100\", \"summary\": \"High CPU spike on worker-01 requiring investigation.\", \"action_required\": true}], \"needs_attention\": [], \"informational\": [], \"actions\": [{\"id\": \"m_100\", \"action\": \"Investigate worker-01 CPU\", \"reason\": \"Critical alert from alerts@infra.com\"}]}}"
                                }
                            ]
                        }
                    }
                ]
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(geminiRespJson))

        // Execute Pipeline Steps
        val gmailService = GmailApiService(
            baseUrl = mockServer.url("/gmail").toString().removeSuffix("/")
        )
        val emails = gmailService.fetchAccountEmails(account, "mock_access_token")
        assertEquals(1, emails.size)
        assertEquals("m_100", emails.first().id)

        // Deduplication
        val (deduped, _) = EmailDeduplicator.deduplicate(emails)
        assertEquals(1, deduped.size)

        // Gemini AI Synthesis
        val apiKeyManager = GeminiApiKeyManager(storage)
        val summarizer = GeminiSummarizer(
            apiKeyManager = apiKeyManager,
            baseUrl = mockServer.url("/gemini").toString().removeSuffix("/")
        )
        val briefingResult = summarizer.summarize(deduped, listOf(account), mode = "morning")
        assertTrue(briefingResult.isSuccess)

        val briefingData = briefingResult.getOrThrow()
        assertEquals("morning", briefingData.briefing_type)
        assertEquals(1, briefingData.morning_briefing.important.size)
        val imp = briefingData.morning_briefing.important.first()
        assertEquals("m_100", imp.id)
        assertEquals("acc_work", imp.account_id)
        assertEquals("WORK", imp.account_label)
        assertEquals("#3B82F6", imp.account_color_hex)
        assertEquals("alerts@infra.com", imp.sender)
        assertTrue(imp.action_required)

        assertEquals(1, briefingData.morning_briefing.actions.size)
        val act = briefingData.morning_briefing.actions.first()
        assertEquals("m_100", act.id)
        assertEquals("Investigate worker-01 CPU", act.action)
        assertEquals("acc_work", act.account_id)
        assertEquals("WORK", act.account_label)
        assertEquals("#3B82F6", act.account_color_hex)
    }
}
