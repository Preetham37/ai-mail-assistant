package com.preetham.aimailassistant.data

import com.google.gson.annotations.SerializedName

data class AccountMeta(
    @SerializedName(value = "id", alternate = ["account_id", "accountId"])
    val id: String = "primary",

    @SerializedName(value = "email", alternate = ["email_address", "account_email", "emailAddress"])
    val email: String = "",

    @SerializedName(value = "display_name", alternate = ["displayName"])
    val display_name: String = "",

    @SerializedName(value = "label", alternate = ["account_label", "accountLabel"])
    val label: String = "PERSONAL",

    @SerializedName(value = "color_hex", alternate = ["account_color_hex", "accountColorHex"])
    val color_hex: String = "#3B82F6",

    @SerializedName(value = "is_active", alternate = ["isActive", "enabled"])
    val is_active: Boolean = true
)

data class BriefingData(
    val briefing_type: String = "morning",
    val generated_at: String = "",
    val accounts: List<AccountMeta> = emptyList(),
    val morning_briefing: MorningBriefing = MorningBriefing(),
    val email_details: Map<String, EmailDetail> = emptyMap()
)

data class MorningBriefing(
    val important: List<BriefingItem> = emptyList(),
    val needs_attention: List<BriefingItem> = emptyList(),
    val informational: List<TopicItem> = emptyList(),
    val actions: List<ActionItem> = emptyList()
)

data class BriefingItem(
    val id: String = "",
    val sender: String = "",
    val subject: String = "",
    val summary: String = "",
    val action_required: Boolean = false,
    @SerializedName(value = "account_id", alternate = ["accountId"])
    val account_id: String = "primary",
    @SerializedName(value = "account_label", alternate = ["accountLabel"])
    val account_label: String = "PERSONAL",
    @SerializedName(value = "account_color_hex", alternate = ["accountColorHex"])
    val account_color_hex: String = "#3B82F6"
)

data class TopicItem(
    val ids: List<String> = emptyList(),
    val topic: String = "",
    val summary: String = "",
    @SerializedName(value = "account_id", alternate = ["accountId"])
    val account_id: String = "primary",
    @SerializedName(value = "account_label", alternate = ["accountLabel"])
    val account_label: String = "PERSONAL",
    @SerializedName(value = "account_color_hex", alternate = ["accountColorHex"])
    val account_color_hex: String = "#3B82F6"
)

data class ActionItem(
    val id: String = "",
    val action: String = "",
    val reason: String = "",
    @SerializedName(value = "account_id", alternate = ["accountId"])
    val account_id: String = "primary",
    @SerializedName(value = "account_label", alternate = ["accountLabel"])
    val account_label: String = "PERSONAL",
    @SerializedName(value = "account_color_hex", alternate = ["accountColorHex"])
    val account_color_hex: String = "#3B82F6"
)

data class EmailDetail(
    val id: String = "",
    val sender: String = "",
    val subject: String = "",
    val date: String = "",
    val body: String = "",
    @SerializedName(value = "account_id", alternate = ["accountId"])
    val account_id: String = "primary",
    @SerializedName(value = "account_label", alternate = ["accountLabel"])
    val account_label: String = "PERSONAL",
    @SerializedName(value = "account_color_hex", alternate = ["accountColorHex"])
    val account_color_hex: String = "#3B82F6",
    @SerializedName(value = "account_email", alternate = ["accountEmail"])
    val account_email: String = ""
)
