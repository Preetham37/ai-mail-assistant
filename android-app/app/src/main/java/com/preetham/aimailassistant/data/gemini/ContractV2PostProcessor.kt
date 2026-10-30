package com.preetham.aimailassistant.data.gemini

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.preetham.aimailassistant.data.*
import java.text.SimpleDateFormat
import java.util.*

object ContractV2PostProcessor {

    private val gson = Gson()

    fun process(
        rawAiJson: String,
        emails: List<RawEmail>,
        accounts: List<AccountMeta>,
        mode: String = "morning"
    ): BriefingData {
        val cleanJson = rawAiJson.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val defaultAcc = accounts.firstOrNull { it.is_active }
            ?: accounts.firstOrNull()
            ?: AccountMeta(
                id = "primary",
                label = "PERSONAL",
                color_hex = "#3B82F6"
            )

        val accountMap = accounts.associateBy { it.id }

        // Build email details lookup
        val emailsLookup = mutableMapOf<String, EmailDetail>()
        for (m in emails) {
            val detail = EmailDetail(
                id = m.id,
                sender = m.sender,
                subject = m.subject,
                date = m.date,
                body = m.body,
                account_id = m.accountId,
                account_label = m.accountLabel,
                account_color_hex = m.accountColorHex,
                account_email = m.accountEmail
            )
            emailsLookup[m.id] = detail
            for (dupId in m.duplicateIds) {
                emailsLookup[dupId] = detail.copy(id = dupId)
            }
        }

        // Parse root JSON
        val parsedData = try {
            val rootElement = JsonParser.parseString(cleanJson)
            if (rootElement.isJsonObject) {
                val rootObj = rootElement.asJsonObject
                if (rootObj.has("morning_briefing")) {
                    gson.fromJson(rootObj, BriefingData::class.java)
                } else {
                    // In case the model returned the inner morning_briefing object directly
                    val mb = gson.fromJson(rootObj, MorningBriefing::class.java)
                    BriefingData(morning_briefing = mb)
                }
            } else {
                BriefingData()
            }
        } catch (e: Exception) {
            BriefingData()
        }

        val mb = parsedData.morning_briefing

        // Hydrate Important
        val hydratedImportant = mb.important.map { item ->
            hydrateBriefingItem(item, emailsLookup, accountMap, defaultAcc)
        }

        // Hydrate Needs Attention
        val hydratedAttention = mb.needs_attention.map { item ->
            hydrateBriefingItem(item, emailsLookup, accountMap, defaultAcc)
        }

        // Hydrate Actions
        val hydratedActions = mb.actions.map { item ->
            hydrateActionItem(item, emailsLookup, accountMap, defaultAcc)
        }

        // Hydrate Informational Topics
        val hydratedTopics = mb.informational.map { topic ->
            val accIds = mutableListOf<String>()
            val accLabels = mutableListOf<String>()

            for (mid in topic.ids) {
                val detail = emailsLookup[mid]
                if (detail != null) {
                    if (!accIds.contains(detail.account_id)) {
                        accIds.add(detail.account_id)
                        accLabels.add(detail.account_label)
                    }
                }
            }

            if (accIds.isEmpty()) {
                if (topic.account_ids.isNotEmpty()) {
                    accIds.addAll(topic.account_ids)
                } else if (topic.account_id.isNotBlank() && topic.account_id != "primary") {
                    accIds.add(topic.account_id)
                } else {
                    accIds.add(defaultAcc.id)
                }
            }

            if (accLabels.isEmpty()) {
                if (topic.account_labels.isNotEmpty()) {
                    accLabels.addAll(topic.account_labels)
                } else if (topic.account_label.isNotBlank() && topic.account_label != "PERSONAL") {
                    accLabels.add(topic.account_label)
                } else {
                    accLabels.add(defaultAcc.label)
                }
            }

            val primaryAccId = accIds.firstOrNull() ?: defaultAcc.id
            val primaryAccMeta = accountMap[primaryAccId] ?: defaultAcc

            topic.copy(
                account_id = primaryAccId,
                account_label = accLabels.firstOrNull() ?: primaryAccMeta.label,
                account_color_hex = primaryAccMeta.color_hex,
                account_ids = accIds.distinct(),
                account_labels = accLabels.distinct()
            )
        }

        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        return BriefingData(
            briefing_type = mode,
            generated_at = nowIso,
            accounts = accounts,
            morning_briefing = MorningBriefing(
                important = hydratedImportant,
                needs_attention = hydratedAttention,
                informational = hydratedTopics,
                actions = hydratedActions
            ),
            email_details = emailsLookup
        )
    }

    private fun hydrateBriefingItem(
        item: BriefingItem,
        emailsLookup: Map<String, EmailDetail>,
        accountMap: Map<String, AccountMeta>,
        defaultAcc: AccountMeta
    ): BriefingItem {
        val detail = emailsLookup[item.id]
        val resolvedAccountId = item.account_id.takeIf { it.isNotBlank() && it != "primary" && it != "default" }
            ?: detail?.account_id
            ?: defaultAcc.id

        val accMeta = accountMap[resolvedAccountId] ?: defaultAcc
        val resolvedLabel = item.account_label.takeIf { it.isNotBlank() && it != "PERSONAL" && it != "PRIMARY" }
            ?: detail?.account_label
            ?: accMeta.label

        val resolvedColor = item.account_color_hex.takeIf { it.isNotBlank() && it.startsWith("#") && it != "#3B82F6" }
            ?: detail?.account_color_hex
            ?: accMeta.color_hex

        val resolvedSender = item.sender.ifBlank { detail?.sender ?: "Unknown Sender" }
        val resolvedSubject = item.subject.ifBlank { detail?.subject ?: "(No Subject)" }

        return item.copy(
            sender = resolvedSender,
            subject = resolvedSubject,
            account_id = resolvedAccountId,
            account_label = resolvedLabel,
            account_color_hex = resolvedColor
        )
    }

    private fun hydrateActionItem(
        item: ActionItem,
        emailsLookup: Map<String, EmailDetail>,
        accountMap: Map<String, AccountMeta>,
        defaultAcc: AccountMeta
    ): ActionItem {
        val detail = emailsLookup[item.id]
        val resolvedAccountId = item.account_id.takeIf { it.isNotBlank() && it != "primary" && it != "default" }
            ?: detail?.account_id
            ?: defaultAcc.id

        val accMeta = accountMap[resolvedAccountId] ?: defaultAcc
        val resolvedLabel = item.account_label.takeIf { it.isNotBlank() && it != "PERSONAL" && it != "PRIMARY" }
            ?: detail?.account_label
            ?: accMeta.label

        val resolvedColor = item.account_color_hex.takeIf { it.isNotBlank() && it.startsWith("#") && it != "#3B82F6" }
            ?: detail?.account_color_hex
            ?: accMeta.color_hex

        return item.copy(
            account_id = resolvedAccountId,
            account_label = resolvedLabel,
            account_color_hex = resolvedColor
        )
    }
}
