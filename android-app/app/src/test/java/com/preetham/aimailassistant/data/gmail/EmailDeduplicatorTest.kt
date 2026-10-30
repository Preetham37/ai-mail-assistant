package com.preetham.aimailassistant.data.gmail

import com.preetham.aimailassistant.data.RawEmail
import org.junit.Assert.*
import org.junit.Test

class EmailDeduplicatorTest {

    @Test
    fun `computeFingerprint uses RFC Message-ID when present`() {
        val mail1 = RawEmail(
            id = "msg_001",
            accountId = "acc_work",
            accountLabel = "WORK",
            accountColorHex = "#3B82F6",
            rfcMessageId = "<CAE=12345@mail.gmail.com>",
            subject = "Q3 Roadmap",
            body = "Body content here"
        )
        val mail2 = RawEmail(
            id = "msg_002",
            accountId = "acc_personal",
            accountLabel = "PERSONAL",
            accountColorHex = "#8B5CF6",
            rfcMessageId = " <CAE=12345@mail.gmail.com> ",
            subject = "Fwd: Q3 Roadmap",
            body = "Different forwarded text"
        )

        val fp1 = EmailDeduplicator.computeFingerprint(mail1)
        val fp2 = EmailDeduplicator.computeFingerprint(mail2)

        assertEquals("rfc:<cae=12345@mail.gmail.com>", fp1)
        assertEquals(fp1, fp2)
    }

    @Test
    fun `computeFingerprint uses normalized subject and body hash when RFC Message-ID is absent`() {
        val mail1 = RawEmail(
            id = "msg_01",
            accountId = "acc_work",
            accountLabel = "WORK",
            accountColorHex = "#3B82F6",
            rfcMessageId = "",
            subject = "Re: Team Standup Updates",
            body = "Hey team, here are my sprint updates for today."
        )
        val mail2 = RawEmail(
            id = "msg_02",
            accountId = "acc_personal",
            accountLabel = "PERSONAL",
            accountColorHex = "#8B5CF6",
            rfcMessageId = "",
            subject = "Fwd: team standup updates",
            body = "Hey team, here are my sprint updates for today."
        )

        val fp1 = EmailDeduplicator.computeFingerprint(mail1)
        val fp2 = EmailDeduplicator.computeFingerprint(mail2)

        assertTrue(fp1.startsWith("content:team standup updates|"))
        assertEquals(fp1, fp2)
    }

    @Test
    fun `deduplicate merges accounts and records duplicate IDs`() {
        val workMail = RawEmail(
            id = "work_msg_101",
            accountId = "acc_work",
            accountLabel = "WORK",
            accountColorHex = "#3B82F6",
            rfcMessageId = "<alert_42@pagerduty.com>",
            subject = "CRITICAL: Database Load High",
            body = "Database CPU exceeded threshold."
        )
        val personalMail = RawEmail(
            id = "personal_msg_202",
            accountId = "acc_personal",
            accountLabel = "PERSONAL",
            accountColorHex = "#8B5CF6",
            rfcMessageId = "<alert_42@pagerduty.com>",
            subject = "CRITICAL: Database Load High",
            body = "Database CPU exceeded threshold."
        )
        val otherMail = RawEmail(
            id = "dev_msg_303",
            accountId = "acc_dev",
            accountLabel = "DEV",
            accountColorHex = "#10B981",
            rfcMessageId = "<commit_99@github.com>",
            subject = "New PR merged",
            body = "PR #10 was merged into main branch."
        )

        val (deduped, dupMappings) = EmailDeduplicator.deduplicate(listOf(workMail, personalMail, otherMail))

        // There should be 2 unique emails
        assertEquals(2, deduped.size)

        val primaryAlert = deduped.first { it.id == "work_msg_101" }
        assertEquals(listOf("acc_work", "acc_personal"), primaryAlert.allAccountIds)
        assertEquals(listOf("WORK", "PERSONAL"), primaryAlert.allAccountLabels)
        assertEquals(listOf("personal_msg_202"), primaryAlert.duplicateIds)

        // Duplicate mappings
        assertTrue(dupMappings.containsKey("personal_msg_202"))
        assertEquals("work_msg_101", dupMappings["personal_msg_202"]?.id)
    }
}
