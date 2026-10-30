package com.preetham.aimailassistant.data.gemini

import com.preetham.aimailassistant.data.RawEmail
import org.junit.Assert.*
import org.junit.Test

class GeminiPromptBuilderTest {

    @Test
    fun `buildPrompt injects morning context in morning mode`() {
        val emails = listOf(
            RawEmail(
                id = "msg_1",
                accountId = "acc_work",
                accountLabel = "WORK",
                accountColorHex = "#3B82F6",
                sender = "Alice <alice@work.com>",
                subject = "Morning Report",
                body = "Here is the morning report"
            )
        )

        val prompt = GeminiPromptBuilder.buildPrompt(emails, "morning")

        assertTrue(prompt.contains("concise morning briefing"))
        assertTrue(prompt.contains("IMPORTANT RULES:"))
        assertTrue(prompt.contains("MULTI-ACCOUNT & CROSS-ACCOUNT RULES:"))
        assertTrue(prompt.contains("26. Maintain strict separation of work vs personal priorities"))
        assertTrue(prompt.contains("msg_1"))
        assertTrue(prompt.contains("acc_work"))
        assertTrue(prompt.contains("WORK"))
    }

    @Test
    fun `buildPrompt injects evening context in evening mode`() {
        val emails = listOf(
            RawEmail(
                id = "msg_2",
                accountId = "acc_personal",
                accountLabel = "PERSONAL",
                accountColorHex = "#8B5CF6",
                sender = "Bob <bob@mail.com>",
                subject = "Evening Wrap",
                body = "Here is the evening wrap up"
            )
        )

        val prompt = GeminiPromptBuilder.buildPrompt(emails, "evening")

        assertTrue(prompt.contains("evening briefing and workday wrap-up"))
        assertTrue(prompt.contains("msg_2"))
    }
}
