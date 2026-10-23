package com.preetham.aimailassistant.data

data class BriefingData(
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
    val action_required: Boolean = false
)

data class TopicItem(
    val ids: List<String> = emptyList(),
    val topic: String = "",
    val summary: String = ""
)

data class ActionItem(
    val id: String = "",
    val action: String = "",
    val reason: String = ""
)

data class EmailDetail(
    val id: String = "",
    val sender: String = "",
    val subject: String = "",
    val date: String = "",
    val body: String = ""
)
