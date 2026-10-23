package com.preetham.aimailassistant

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preetham.aimailassistant.data.BriefingData
import com.preetham.aimailassistant.data.BriefingRepository
import com.preetham.aimailassistant.data.EmailDetail
import com.preetham.aimailassistant.ui.screens.BriefingScreen
import com.preetham.aimailassistant.ui.screens.ChecklistScreen
import com.preetham.aimailassistant.ui.screens.EmailDetailScreen
import com.preetham.aimailassistant.ui.screens.TopicsScreen
import com.preetham.aimailassistant.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = BriefingRepository(applicationContext)

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember {
                mutableStateOf(repository.getNightMode() ?: systemDark)
            }

            BriefingOSTheme(darkTheme = isDarkTheme) {
                MainAppScreen(
                    repository = repository,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        val newMode = !isDarkTheme
                        isDarkTheme = newMode
                        repository.setNightMode(newMode)
                    }
                )
            }
        }
    }
}

@Composable
fun MainAppScreen(
    repository: BriefingRepository,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var data by remember { mutableStateOf(BriefingData()) }
    var completedActions by remember { mutableStateOf(repository.getCompletedActionIds()) }
    var selectedTab by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Dedicated Full-Screen Email Detail State (replaces jittery bottom sheet completely)
    var selectedEmail by remember { mutableStateOf<EmailDetail?>(null) }
    var selectedSummary by remember { mutableStateOf<String?>(null) }

    // Initial load
    LaunchedEffect(Unit) {
        val loaded = repository.loadData()
        data = loaded
    }

    val remainingActionsCount = remember(data, completedActions) {
        data.morning_briefing.actions.count { !completedActions.contains(it.id) }
    }

    fun openEmailDetail(id: String, summary: String? = null) {
        val detail = data.email_details[id]
        if (detail != null) {
            selectedEmail = detail
            selectedSummary = summary
        } else {
            Toast.makeText(context, "ERROR: RECORD NOT IN BUFFER", Toast.LENGTH_SHORT).show()
        }
    }

    // If an email is selected, open it COMPLETELY in full-screen reader view
    if (selectedEmail != null) {
        EmailDetailScreen(
            email = selectedEmail!!,
            summary = selectedSummary,
            onBack = {
                selectedEmail = null
                selectedSummary = null
            }
        )
        return
    }

    Scaffold(
        containerColor = RetroCreamBg,
        topBar = {
            // Retro Classic Console Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(RetroCreamSurface)
                    .border(2.dp, RetroBlack)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(RetroBlack)
                                .border(1.dp, RetroBlack)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SYS.BRIEFING_OS //",
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = RetroTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(RetroBlack)
                                .border(1.dp, RetroBlack)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "S24_ROM",
                                color = RetroInverseText,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // Top Action Controls (Night Mode Toggle + Sync Button)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Night / Day Mode Toggle Button
                        Box(
                            modifier = Modifier
                                .background(RetroWhite)
                                .border(2.dp, RetroBlack)
                                .clickable {
                                    onToggleTheme()
                                    val msg = if (!isDarkTheme) "NIGHT CONSOLE ACTIVATED" else "DAY PAPER ACTIVATED"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle Night Mode",
                                    tint = RetroTextPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isDarkTheme) "[DAY]" else "[NIGHT]",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RetroTextPrimary
                                )
                            }
                        }

                        // Retro Sync Push Button (Icon only)
                        Box(
                            modifier = Modifier
                                .background(if (isRefreshing) RetroInverseBg else RetroWhite)
                                .border(2.dp, RetroBlack)
                                .clickable {
                                    coroutineScope.launch {
                                        isRefreshing = true
                                        val result = repository.refreshFromServer()
                                        if (result.isSuccess) {
                                            data = result.getOrThrow()
                                            Toast.makeText(context, "SYNC SUCCESS: 100%", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "OFFLINE CACHE LOADED", Toast.LENGTH_SHORT).show()
                                        }
                                        isRefreshing = false
                                    }
                                }
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync",
                                tint = if (isRefreshing) RetroInverseText else RetroTextPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Retro Hardware Bottom Deck
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(RetroCreamSurface)
                    .border(2.dp, RetroBlack)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Button 0: Briefing
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selectedTab == 0) RetroInverseBg else RetroWhite)
                            .border(2.dp, RetroBlack)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[01. PULSE]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (selectedTab == 0) RetroInverseText else RetroTextPrimary
                        )
                    }

                    // Button 1: Tasks
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .background(if (selectedTab == 1) RetroInverseBg else RetroWhite)
                            .border(2.dp, RetroBlack)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "[02. TASKS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = if (selectedTab == 1) RetroInverseText else RetroTextPrimary
                            )
                            if (remainingActionsCount > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(if (selectedTab == 1) RetroWhite else RetroInverseBg)
                                        .border(1.dp, RetroBlack)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "$remainingActionsCount",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (selectedTab == 1) RetroTextPrimary else RetroInverseText
                                    )
                                }
                            }
                            Text(
                                text = "]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = if (selectedTab == 1) RetroInverseText else RetroTextPrimary
                            )
                        }
                    }

                    // Button 2: Archive / Topics
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selectedTab == 2) RetroInverseBg else RetroWhite)
                            .border(2.dp, RetroBlack)
                            .clickable { selectedTab = 2 }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[03. VAULT]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (selectedTab == 2) RetroInverseText else RetroTextPrimary
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> BriefingScreen(
                    data = data,
                    completedActionIds = completedActions,
                    onSelectEmail = { id, summary -> openEmailDetail(id, summary) },
                    onNavigateToChecklist = { selectedTab = 1 }
                )
                1 -> ChecklistScreen(
                    actions = data.morning_briefing.actions,
                    completedIds = completedActions,
                    onToggleAction = { id ->
                        completedActions = repository.toggleActionCompleted(id)
                    },
                    onSelectEmail = { id -> openEmailDetail(id, null) }
                )
                2 -> TopicsScreen(
                    topics = data.morning_briefing.informational,
                    emails = data.email_details,
                    onSelectEmail = { id, summary -> openEmailDetail(id, summary) }
                )
            }
        }
    }
}
