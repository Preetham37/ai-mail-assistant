package com.preetham.aimailassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preetham.aimailassistant.data.ActionItem
import com.preetham.aimailassistant.ui.components.RetroBox
import com.preetham.aimailassistant.ui.theme.*

@Composable
fun ChecklistScreen(
    actions: List<ActionItem>,
    completedIds: Set<String>,
    onToggleAction: (String) -> Unit,
    onSelectEmail: (id: String) -> Unit
) {
    val totalCount = actions.size
    val completedCount = actions.count { completedIds.contains(it.id) }
    val percent = if (totalCount > 0) (completedCount * 100) / totalCount else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Retro Progress Bar Card (Macintosh Monochrome Meter)
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
                        Column {
                            Text(
                                text = "CHECKLIST_PROCESSOR.EXE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetroBlack
                            )
                            Text(
                                text = "BUFFER: $completedCount / $totalCount RESOLVED",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetroTextPrimary
                            )
                        }

                        // Retro Inverted Percent Badge
                        Box(
                            modifier = Modifier
                                .background(RetroBlack)
                                .border(2.dp, RetroBlack)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$percent%",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = RetroInverseText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Retro Segmented Black & White Meter
                    val totalBlocks = 12
                    val activeBlocks = if (totalCount > 0) ((completedCount.toFloat() / totalCount) * totalBlocks).toInt() else 0

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RetroCreamSurface)
                            .border(2.dp, RetroBlack)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        for (i in 0 until totalBlocks) {
                            val isActive = i < activeBlocks
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(12.dp)
                                    .background(if (isActive) RetroBlack else RetroWhite)
                                    .border(1.dp, RetroBlack)
                            )
                        }
                    }
                }
            }
        }

        // Section Title
        item {
            Text(
                text = ">>> QUEUE: PENDING_USER_ACTIONS.LOG",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = RetroBlack,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 2.dp)
            )
        }

        if (actions.isEmpty()) {
            item {
                RetroBox(
                    backgroundColor = RetroWhite,
                    borderColor = RetroBlack,
                    shadowOffset = 3.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[SYSTEM_IDLE: NO_PENDING_TASKS]",
                            fontFamily = FontFamily.Monospace,
                            color = RetroMediumGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        items(actions) { action ->
            val isDone = completedIds.contains(action.id)
            RetroMonochromeActionCardItem(
                action = action,
                isDone = isDone,
                onToggle = { onToggleAction(action.id) },
                onViewEmail = { onSelectEmail(action.id) }
            )
        }
    }
}

@Composable
fun RetroMonochromeActionCardItem(
    action: ActionItem,
    isDone: Boolean,
    onToggle: () -> Unit,
    onViewEmail: () -> Unit
) {
    RetroBox(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        backgroundColor = if (isDone) RetroCreamSurface else RetroWhite,
        borderColor = RetroBlack,
        shadowColor = RetroBlack,
        shadowOffset = if (isDone) 2.dp else 4.dp,
        cornerRadius = 4.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Classic Square Monochrome Checkbox [ ] or [✓]
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(24.dp)
                        .background(if (isDone) RetroBlack else RetroWhite)
                        .border(2.dp, RetroBlack)
                        .clickable { onToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done",
                            tint = RetroWhite,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = action.action,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDone) RetroMediumGray else RetroTextPrimary,
                        textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = action.reason,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (isDone) RetroMediumGray.copy(alpha = 0.7f) else RetroTextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Button to view email
                    Row(
                        modifier = Modifier
                            .background(RetroCreamSurface)
                            .border(1.dp, RetroBlack)
                            .clickable { onViewEmail() }
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            tint = RetroBlack,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "> OPEN_ORIGIN_MSG",
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
