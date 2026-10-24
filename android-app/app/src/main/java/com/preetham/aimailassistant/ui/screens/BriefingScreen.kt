package com.preetham.aimailassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preetham.aimailassistant.data.BriefingData
import com.preetham.aimailassistant.data.BriefingItem
import com.preetham.aimailassistant.ui.components.AccountBadge
import com.preetham.aimailassistant.ui.components.KpiCard
import com.preetham.aimailassistant.ui.components.RetroBox
import com.preetham.aimailassistant.ui.theme.*

@Composable
fun BriefingScreen(
    data: BriefingData,
    completedActionIds: Set<String>,
    onSelectEmail: (id: String, summary: String?) -> Unit,
    onNavigateToChecklist: () -> Unit
) {
    val morning = data.morning_briefing
    val totalEmails = data.email_details.size
    val totalActions = morning.actions.size
    val completedActions = morning.actions.count { completedActionIds.contains(it.id) }
    val attentionCount = morning.needs_attention.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Retro Macintosh Boot Card
        item {
            RetroBox(
                backgroundColor = RetroWhite,
                borderColor = RetroBlack,
                shadowColor = RetroBlack,
                shadowOffset = 4.dp,
                cornerRadius = 4.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SYS://BRIEFING_OS.ROM",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetroBlack,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(RetroBlack)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "READY ●",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetroInverseText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "INBOX_PULSE_FEED //",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = RetroTextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "> Gemini 3.8-Flash Neural Ingestion Engine",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = RetroMediumGray
                    )
                }
            }
        }

        // KPI Readouts Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard(
                    modifier = Modifier.weight(1f),
                    title = "INBOX",
                    value = "$totalEmails",
                    icon = Icons.Default.MarkEmailRead,
                    accentColor = LevelImportantAccent
                )
                KpiCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateToChecklist() },
                    title = "TASKS",
                    value = "$completedActions/$totalActions",
                    icon = Icons.Default.Checklist,
                    accentColor = LevelActionAccent
                )
                KpiCard(
                    modifier = Modifier.weight(1f),
                    title = "ALERTS",
                    value = "$attentionCount",
                    icon = Icons.Default.WarningAmber,
                    accentColor = if (attentionCount > 0) LevelSecurityAccent else null
                )
            }
        }

        // Section: Attention Required
        if (morning.needs_attention.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = ">>> QUEUE: ATTENTION_REQUIRED",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = LevelActionAccent,
                        letterSpacing = 1.sp
                    )
                }
            }

            items(morning.needs_attention) { item ->
                RetroMonochromeBriefingCard(
                    item = item,
                    tagLabel = "[ACTION_REQD]",
                    accentColor = LevelActionAccent,
                    tagBgColor = LevelActionBg,
                    tagTextColor = LevelActionText,
                    onClick = { onSelectEmail(item.id, item.summary) }
                )
            }
        }

        // Section: Important & Security
        if (morning.important.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = ">>> QUEUE: SECURITY_&_URGENT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = LevelSecurityAccent,
                        letterSpacing = 1.sp
                    )
                }
            }

            items(morning.important) { item ->
                val isSecurity = item.subject.contains("password", ignoreCase = true) ||
                                 item.subject.contains("security", ignoreCase = true) ||
                                 item.subject.contains("verification", ignoreCase = true)

                if (isSecurity) {
                    RetroMonochromeBriefingCard(
                        item = item,
                        tagLabel = "[SEC_ALERT]",
                        accentColor = LevelSecurityAccent,
                        tagBgColor = LevelSecurityBg,
                        tagTextColor = LevelSecurityText,
                        onClick = { onSelectEmail(item.id, item.summary) }
                    )
                } else {
                    RetroMonochromeBriefingCard(
                        item = item,
                        tagLabel = "[IMPORTANT]",
                        accentColor = LevelImportantAccent,
                        tagBgColor = LevelImportantBg,
                        tagTextColor = LevelImportantText,
                        onClick = { onSelectEmail(item.id, item.summary) }
                    )
                }
            }
        }
    }
}

@Composable
fun RetroMonochromeBriefingCard(
    item: BriefingItem,
    tagLabel: String,
    accentColor: androidx.compose.ui.graphics.Color,
    tagBgColor: androidx.compose.ui.graphics.Color,
    tagTextColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    RetroBox(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        backgroundColor = RetroWhite,
        borderColor = RetroBlack,
        shadowColor = RetroBlack,
        shadowOffset = 4.dp,
        cornerRadius = 4.dp,
        leftStripeColor = accentColor
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Tag + Sender
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(tagBgColor)
                            .border(1.5.dp, accentColor)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = tagLabel,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = tagTextColor,
                            letterSpacing = 0.5.sp
                        )
                    }

                    if (item.account_label.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        AccountBadge(
                            label = item.account_label,
                            colorHex = item.account_color_hex
                        )
                    }
                }

                Text(
                    text = item.sender.substringBefore("<").trim().ifEmpty { item.sender },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = RetroMediumGray,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subject
            Text(
                text = item.subject,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = RetroTextPrimary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Summary
            Text(
                text = item.summary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = RetroTextSecondary,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Prompt link
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "> READ TRANSMISSION",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = accentColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
