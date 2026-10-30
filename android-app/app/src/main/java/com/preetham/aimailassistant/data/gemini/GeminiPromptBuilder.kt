package com.preetham.aimailassistant.data.gemini

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.preetham.aimailassistant.data.RawEmail

object GeminiPromptBuilder {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun buildPrompt(emails: List<RawEmail>, mode: String = "morning"): String {
        val isEvening = mode.equals("evening", ignoreCase = true)
        val briefingContext = if (isEvening) {
            "Your job is to create a concise evening briefing and workday wrap-up across all user inboxes. " +
            "Review today's correspondence, highlight pending responses, action items, and group newsletters into digest topics."
        } else {
            "Your job is to create a concise morning briefing across all user inboxes. " +
            "Put important security, account, payment, or urgent emails first, followed by today's action checklist and informational digest topics."
        }

        val emailPayloadList = emails.map { m ->
            mapOf(
                "id" to m.id,
                "account_id" to m.accountId,
                "account_label" to m.accountLabel,
                "account_color_hex" to m.accountColorHex,
                "all_account_labels" to m.allAccountLabels.ifEmpty { listOf(m.accountLabel) },
                "sender" to m.sender,
                "subject" to m.subject,
                "date" to m.date,
                "body" to m.body
            )
        }
        val aiInputJson = gson.toJson(emailPayloadList)

        return """
You are an AI email assistant.

Analyze the following emails from the user's inbox(es).
The emails are provided as JSON.

$briefingContext

IMPORTANT RULES:
1. Put important security, account, payment, or urgent emails first.
2. Group related emails together when useful.
3. Clearly identify emails that explicitly require the user's action.
4. Separate actionable emails from informational emails.
5. Ignore unnecessary promotional details and boilerplate.
6. Summarize important emails in 1-2 sentences.
7. Do not invent information.
8. Do not make assumptions that are not supported by the email.
9. Only use information present in the provided emails.
10. If an email says something like "if this wasn't you", preserve that condition.
11. Do not automatically say the user must take action when the email only says to act if something was unauthorized or unexpected.
12. Clearly distinguish between:
    - action explicitly requested by the email
    - action that may be useful based on the email
13. Never invent an email ID.
14. Never modify an email ID.
15. Never omit the email ID when referring to a specific email.
16. Copy email IDs exactly as provided.
17. If there are no actions required, return an empty actions array.
18. For every summary, action, and reason, use only information explicitly present in the corresponding email.
19. Do not infer technical details, permissions, scopes, risks, motives, or consequences unless they are explicitly stated in the email.
20. The "reason" field must explain why the email itself indicates that the action may be relevant. Do not add external knowledge.
21. If the email does not provide enough information for a reason, use a simple statement based directly on the email.
22. Never report "open the email in an HTML-compatible client" as an action. Extract the underlying message.

MULTI-ACCOUNT & CROSS-ACCOUNT RULES:
23. Every item in "important", "needs_attention", and "actions" MUST contain the exact "account_id", "account_label", and "account_color_hex" matching the originating email in EMAILS.
24. If an email has "all_account_labels" with multiple accounts, summarize it ONCE, and note both accounts if relevant.
25. For items in "informational", populate "account_ids" (array) and "account_labels" (array) representing all accounts associated with the grouped emails.
26. Maintain strict separation of work vs personal priorities: prioritize urgent work action items during morning workdays, and keep personal urgent banking/security alerts prominently surfaced.

RETURN ONLY VALID JSON.
Do not use Markdown formatting.

Use exactly this JSON structure:
{
    "morning_briefing": {
        "important": [
            {
                "id": "email_id",
                "account_id": "account_id",
                "account_label": "account_label",
                "account_color_hex": "#HEX_COLOR",
                "sender": "sender",
                "subject": "subject",
                "summary": "short summary",
                "action_required": true
            }
        ],
        "needs_attention": [
            {
                "id": "email_id",
                "account_id": "account_id",
                "account_label": "account_label",
                "account_color_hex": "#HEX_COLOR",
                "sender": "sender",
                "subject": "subject",
                "summary": "short summary",
                "action_required": true
            }
        ],
        "informational": [
            {
                "ids": ["email_id"],
                "account_ids": ["account_id"],
                "account_labels": ["account_label"],
                "topic": "group topic",
                "summary": "short grouped summary"
            }
        ],
        "actions": [
            {
                "id": "email_id",
                "account_id": "account_id",
                "account_label": "account_label",
                "account_color_hex": "#HEX_COLOR",
                "action": "action the user should consider",
                "reason": "why this action is relevant"
            }
        ]
    }
}

EMAILS:
$aiInputJson
""".trimIndent()
    }
}
