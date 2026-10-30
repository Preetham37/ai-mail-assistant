package com.preetham.aimailassistant.data.gmail

import com.preetham.aimailassistant.data.AccountMeta
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64

class GmailApiServiceTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var apiService: GmailApiService

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        apiService = GmailApiService(
            baseUrl = mockServer.url("/gmail/v1/users/me").toString().removeSuffix("/")
        )
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `fetchAccountEmails lists messages and fetches full bodies`() = runBlocking {
        // 1. Mock list messages response
        val listResponse = """
            {
                "messages": [
                    { "id": "msg_001", "threadId": "th_1" }
                ],
                "resultSizeEstimate": 1
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(listResponse))

        // 2. Mock get single message response
        val encodedBody = Base64.getUrlEncoder().withoutPadding().encodeToString("Hello from Gmail API!".toByteArray())
        val messageResponse = """
            {
                "id": "msg_001",
                "payload": {
                    "mimeType": "text/plain",
                    "headers": [
                        { "name": "From", "value": "test@example.com" },
                        { "name": "Subject", "value": "Test Subject" },
                        { "name": "Date", "value": "2026-10-30" },
                        { "name": "Message-ID", "value": "<test-1@example.com>" }
                    ],
                    "body": {
                        "data": "$encodedBody"
                    }
                }
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(messageResponse))

        val account = AccountMeta(id = "acc_1", label = "WORK", color_hex = "#3B82F6", email = "test@example.com")
        val emails = apiService.fetchAccountEmails(account, "mock_token")

        assertEquals(1, emails.size)
        val mail = emails.first()
        assertEquals("msg_001", mail.id)
        assertEquals("test@example.com", mail.sender)
        assertEquals("Test Subject", mail.subject)
        assertEquals("<test-1@example.com>", mail.rfcMessageId)
        assertEquals("Hello from Gmail API!", mail.body)
    }
}
