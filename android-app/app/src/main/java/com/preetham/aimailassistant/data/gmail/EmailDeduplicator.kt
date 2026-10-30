package com.preetham.aimailassistant.data.gmail

import com.preetham.aimailassistant.data.RawEmail
import java.security.MessageDigest
import java.util.regex.Pattern

object EmailDeduplicator {

    private val RE_FWD_PATTERN = Pattern.compile("^(re|fwd|fw):\\s*", Pattern.CASE_INSENSITIVE)
    private val WORD_PATTERN = Pattern.compile("\\w+")

    /**
     * Computes email fingerprint:
     * 1. If RFC-822 Message-ID header is present, returns 'rfc:<message_id>'.
     * 2. Else normalized subject + SHA-256 hash of first 80 words of body.
     */
    fun computeFingerprint(email: RawEmail): String {
        val rfcId = email.rfcMessageId.trim().lowercase()
        if (rfcId.isNotBlank()) {
            return "rfc:$rfcId"
        }

        val normSubject = RE_FWD_PATTERN.matcher(email.subject.lowercase()).replaceAll("").trim()
        val matcher = WORD_PATTERN.matcher(email.body.lowercase())
        val words = mutableListOf<String>()
        while (matcher.find() && words.size < 80) {
            words.add(matcher.group())
        }
        val normBody = words.joinToString(" ")

        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(normBody.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)

        return "content:$normSubject|$hash"
    }

    /**
     * Deduplicates a list of emails across multiple accounts.
     * Returns:
     * - deduped: List of emails ready for Gemini input (with all_account_labels populated).
     * - duplicateMappings: Map from duplicate message ID -> primary email object.
     */
    fun deduplicate(emails: List<RawEmail>): Pair<List<RawEmail>, Map<String, RawEmail>> {
        val deduped = mutableListOf<RawEmail>()
        val seen = mutableMapOf<String, Int>() // fingerprint -> index in deduped
        val duplicateMappings = mutableMapOf<String, RawEmail>()

        for (mail in emails) {
            val fp = computeFingerprint(mail)
            if (seen.containsKey(fp)) {
                val primaryIdx = seen[fp]!!
                val primary = deduped[primaryIdx]

                // Link duplicate ID and merge account labels
                val updatedDuplicateIds = primary.duplicateIds + mail.id
                val updatedAccountIds = (primary.allAccountIds + mail.accountId).distinct()
                val updatedAccountLabels = (primary.allAccountLabels + mail.accountLabel).distinct()

                val updatedPrimary = primary.copy(
                    duplicateIds = updatedDuplicateIds,
                    allAccountIds = updatedAccountIds,
                    allAccountLabels = updatedAccountLabels
                )
                deduped[primaryIdx] = updatedPrimary
                duplicateMappings[mail.id] = updatedPrimary
            } else {
                val newMail = mail.copy(
                    allAccountIds = listOf(mail.accountId),
                    allAccountLabels = listOf(mail.accountLabel)
                )
                seen[fp] = deduped.size
                deduped.add(newMail)
            }
        }

        return Pair(deduped, duplicateMappings)
    }
}
