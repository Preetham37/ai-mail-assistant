package com.preetham.aimailassistant.data.gemini

import com.preetham.aimailassistant.data.AccountMeta
import com.preetham.aimailassistant.data.RawEmail
import org.junit.Assert.*
import org.junit.Test

class ContractV2PostProcessorTest {

    @Test
    fun `process hydrates missing account metadata from email lookup`() {
        val rawEmail = RawEmail(
            id = "email_123",
            accountId = "acc_work",
            accountLabel = "WORK",
            accountColorHex = "#3B82F6",
            accountEmail = "user@work.com",
            sender = "Boss <boss@work.com>",
            subject = "Urgent: Sign Contract",
            body = "Please sign the NDA today."
        )
        val accounts = listOf(
            AccountMeta(
                id = "acc_work",
                email = "user@work.com",
                label = "WORK",
                color_hex = "#3B82F6"
            )
        )

        // Simulated AI output that omitted account_color_hex and sender
        val aiJson = """
            {
                "morning_briefing": {
                    "important": [
                        {
                            "id": "email_123",
                            "account_id": "acc_work",
                            "account_label": "WORK",
                            "summary": "Boss requested NDA signature today.",
                            "action_required": true
                        }
                    ],
                    "needs_attention": [],
                    "informational": [],
                    "actions": [
                        {
                            "id": "email_123",
                            "action": "Sign NDA",
                            "reason": "Requested by boss"
                        }
                    ]
                }
            }
        """.trimIndent()

        val briefingData = ContractV2PostProcessor.process(
            rawAiJson = aiJson,
            emails = listOf(rawEmail),
            accounts = accounts,
            mode = "morning"
        )

        // Important item assertions
        assertEquals(1, briefingData.morning_briefing.important.size)
        val imp = briefingData.morning_briefing.important.first()
        assertEquals("email_123", imp.id)
        assertEquals("acc_work", imp.account_id)
        assertEquals("WORK", imp.account_label)
        assertEquals("#3B82F6", imp.account_color_hex)
        assertEquals("Boss <boss@work.com>", imp.sender)
        assertEquals("Urgent: Sign Contract", imp.subject)

        // Action item assertions
        assertEquals(1, briefingData.morning_briefing.actions.size)
        val act = briefingData.morning_briefing.actions.first()
        assertEquals("email_123", act.id)
        assertEquals("acc_work", act.account_id)
        assertEquals("WORK", act.account_label)
        assertEquals("#3B82F6", act.account_color_hex)

        // Email details assertions
        assertEquals(1, briefingData.email_details.size)
        val detail = briefingData.email_details["email_123"]
        assertNotNull(detail)
        assertEquals("user@work.com", detail?.account_email)
    }

    @Test
    fun `process populates informational topic account arrays`() {
        val mail1 = RawEmail(
            id = "m1",
            accountId = "acc_work",
            accountLabel = "WORK",
            accountColorHex = "#3B82F6"
        )
        val mail2 = RawEmail(
            id = "m2",
            accountId = "acc_personal",
            accountLabel = "PERSONAL",
            accountColorHex = "#8B5CF6"
        )
        val accounts = listOf(
            AccountMeta(id = "acc_work", label = "WORK", color_hex = "#3B82F6"),
            AccountMeta(id = "acc_personal", label = "PERSONAL", color_hex = "#8B5CF6")
        )

        val aiJson = """
            {
                "morning_briefing": {
                    "important": [],
                    "needs_attention": [],
                    "informational": [
                        {
                            "ids": ["m1", "m2"],
                            "topic": "Cross-Account Newsletters",
                            "summary": "Weekly digests"
                        }
                    ],
                    "actions": []
                }
            }
        """.trimIndent()

        val briefingData = ContractV2PostProcessor.process(
            rawAiJson = aiJson,
            emails = listOf(mail1, mail2),
            accounts = accounts,
            mode = "evening"
        )

        val topic = briefingData.morning_briefing.informational.first()
        assertEquals(listOf("acc_work", "acc_personal"), topic.account_ids)
        assertEquals(listOf("WORK", "PERSONAL"), topic.account_labels)
        assertEquals("evening", briefingData.briefing_type)
    }
}
