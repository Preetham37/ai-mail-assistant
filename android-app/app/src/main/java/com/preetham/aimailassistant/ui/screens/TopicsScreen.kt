package com.preetham.aimailassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preetham.aimailassistant.data.EmailDetail
import com.preetham.aimailassistant.data.TopicItem
import com.preetham.aimailassistant.ui.components.RetroBox
import com.preetham.aimailassistant.ui.theme.*

@Composable
fun TopicsScreen(
    topics: List<TopicItem>,
    emails: Map<String, EmailDetail>,
    onSelectEmail: (id: String, summary: String?) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: Topics, 1: All Emails

    val filteredTopics = remember(searchQuery, topics) {
        if (searchQuery.isBlank()) topics
        else topics.filter {
            it.topic.contains(searchQuery, ignoreCase = true) ||
            it.summary.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredEmails = remember(searchQuery, emails) {
        if (searchQuery.isBlank()) emails.values.toList()
        else emails.values.filter {
            it.subject.contains(searchQuery, ignoreCase = true) ||
            it.sender.contains(searchQuery, ignoreCase = true) ||
            it.body.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Retro DOS Search Input (Cream/White/Black)
        item {
            RetroBox(
                backgroundColor = RetroWhite,
                borderColor = RetroBlack,
                shadowColor = RetroBlack,
                shadowOffset = 3.dp,
                cornerRadius = 4.dp
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "SEARCH_KEYWORD...",
                            color = RetroMediumGray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = RetroBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = RetroBlack
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = RetroWhite,
                        unfocusedContainerColor = RetroWhite,
                        focusedTextColor = RetroTextPrimary,
                        unfocusedTextColor = RetroTextPrimary
                    ),
                    singleLine = true
                )
            }
        }

        // Chunky Monochrome Hardware Tab Switcher
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tab 0: Topics
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selectedTab == 0) RetroBlack else RetroWhite)
                        .border(2.dp, RetroBlack)
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[1. TOPICS (${topics.size})]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 0) RetroInverseText else RetroBlack
                    )
                }

                // Tab 1: All Emails
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selectedTab == 1) RetroBlack else RetroWhite)
                        .border(2.dp, RetroBlack)
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[2. INBOX (${emails.size})]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 1) RetroInverseText else RetroBlack
                    )
                }
            }
        }

        if (selectedTab == 0) {
            // Topics list
            if (filteredTopics.isEmpty()) {
                item {
                    Text(
                        text = "> NO MATCHES IN ARCHIVE.",
                        fontFamily = FontFamily.Monospace,
                        color = RetroMediumGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            items(filteredTopics) { topic ->
                RetroMonochromeTopicCard(
                    topic = topic,
                    emails = emails,
                    onSelectEmail = { id -> onSelectEmail(id, topic.summary) }
                )
            }
        } else {
            // All Emails list
            if (filteredEmails.isEmpty()) {
                item {
                    Text(
                        text = "> NO RECORDS FOUND.",
                        fontFamily = FontFamily.Monospace,
                        color = RetroMediumGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            items(filteredEmails) { email ->
                RetroMonochromeEmailListItem(
                    email = email,
                    onClick = { onSelectEmail(email.id, null) }
                )
            }
        }
    }
}

@Composable
fun RetroMonochromeTopicCard(
    topic: TopicItem,
    emails: Map<String, EmailDetail>,
    onSelectEmail: (String) -> Unit
) {
    RetroBox(
        modifier = Modifier.fillMaxWidth(),
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
                Box(
                    modifier = Modifier
                        .background(RetroBlack)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "[TOPIC_DIGEST]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = RetroInverseText
                    )
                }

                Text(
                    text = "${topic.ids.size} ENCRYPTED RECS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = RetroMediumGray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = topic.topic,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = RetroTextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = topic.summary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = RetroTextSecondary,
                lineHeight = 17.sp
            )

            if (topic.ids.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "> ATTACHED_MESSAGES:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = RetroDarkGray,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(topic.ids) { id ->
                        val email = emails[id]
                        val label = email?.sender?.substringBefore("<")?.trim() ?: "MSG"
                        Box(
                            modifier = Modifier
                                .background(RetroCreamSurface)
                                .border(1.dp, RetroBlack)
                                .clickable { onSelectEmail(id) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "[# $label]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetroBlack
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RetroMonochromeEmailListItem(
    email: EmailDetail,
    onClick: () -> Unit
) {
    RetroBox(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        backgroundColor = RetroWhite,
        borderColor = RetroBlack,
        shadowColor = RetroBlack,
        shadowOffset = 3.dp,
        cornerRadius = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = email.sender.substringBefore("<").trim().ifEmpty { email.sender },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RetroBlack,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                if (email.date.isNotEmpty()) {
                    Text(
                        text = email.date.take(16),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = RetroMediumGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = email.subject.ifEmpty { "(NO_SUBJECT)" },
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = RetroTextPrimary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "> " + email.body.take(100).replace("\n", " ") + "...",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = RetroMediumGray,
                maxLines = 2,
                lineHeight = 15.sp
            )
        }
    }
}
