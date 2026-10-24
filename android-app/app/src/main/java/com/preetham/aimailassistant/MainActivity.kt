package com.preetham.aimailassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.preetham.aimailassistant.data.BriefingData
import com.preetham.aimailassistant.data.BriefingRepository
import com.preetham.aimailassistant.data.EmailDetail
import com.preetham.aimailassistant.ui.components.RetroBox
import com.preetham.aimailassistant.ui.screens.BriefingScreen
import com.preetham.aimailassistant.ui.screens.ChecklistScreen
import com.preetham.aimailassistant.ui.screens.EmailDetailScreen
import com.preetham.aimailassistant.ui.screens.TopicsScreen
import com.preetham.aimailassistant.ui.theme.*
import com.preetham.aimailassistant.util.NotificationHelper
import com.preetham.aimailassistant.util.BriefingAlarmScheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationHelper.createNotificationChannel(applicationContext)
        BriefingAlarmScheduler.scheduleNextBriefing(applicationContext)

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
    var showSyncInfoDialog by remember { mutableStateOf(false) }

    // Dedicated Full-Screen Email Detail State (replaces jittery bottom sheet completely)
    var selectedEmail by remember { mutableStateOf<EmailDetail?>(null) }
    var selectedSummary by remember { mutableStateOf<String?>(null) }

    // Request Runtime Notification Permission on Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* Handled */ }

        LaunchedEffect(Unit) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        val loaded = repository.loadData()
        data = loaded

        // Check for latest updates in background
        val refreshResult = repository.refreshFromServer()
        if (refreshResult.isSuccess) {
            val freshData = refreshResult.getOrThrow()
            data = freshData
            NotificationHelper.triggerBriefingNotification(context, freshData, completedActions)
        }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(RetroCreamSurface)
                    .border(2.dp, RetroBlack)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                            val freshData = result.getOrThrow()
                                            data = freshData
                                            NotificationHelper.triggerBriefingNotification(
                                                context = context,
                                                data = freshData,
                                                completedActionIds = completedActions
                                            )
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

                // Interactive Auto-Sync Status Ribbon / Pill
                val nextSyncTime = remember { BriefingAlarmScheduler.getNextBriefingTimeShort() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDarkTheme) Color(0xFF14241A) else Color(0xFFE8F5E9))
                        .border(1.dp, LevelInfoAccent)
                        .clickable { showSyncInfoDialog = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(LevelInfoAccent)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AUTO-SYNC ACTIVE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = LevelInfoAccent,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "[NEXT: $nextSyncTime] ⓘ",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) DarkLevelInfoText else LightLevelInfoText
                        )
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

    if (showSyncInfoDialog) {
        AutoSyncInfoDialog(
            nextSyncTime = BriefingAlarmScheduler.getNextBriefingTimeShort(),
            onDismiss = { showSyncInfoDialog = false }
        )
    }
}

@Composable
fun AutoSyncInfoDialog(
    nextSyncTime: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        RetroBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            backgroundColor = RetroWhite,
            borderColor = RetroBlack,
            shadowColor = RetroBlack,
            shadowOffset = 5.dp,
            cornerRadius = 6.dp,
            leftStripeColor = LevelInfoAccent
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(LevelInfoAccent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SYS://BACKGROUND_SYNC",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetroTextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(LevelInfoAccent)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ONLINE ●",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = RetroWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "AUTOMATED SYNC PROTOCOL",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = RetroTextPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Automated Background Sync is active. BriefingOS updates daily at 08:00 AM and 06:40 PM IST and posts notifications directly to your lock screen. Manual refresh is completely optional.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = RetroTextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Schedule Details Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RetroCreamSurface)
                        .border(1.5.dp, RetroBlack)
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "MORNING DISPATCH:",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetroTextPrimary
                            )
                            Text(
                                text = "08:00 AM IST",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                color = RetroTextPrimary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "EVENING WRAP-UP:",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetroTextPrimary
                            )
                            Text(
                                text = "06:40 PM IST",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                color = RetroTextPrimary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "NEXT TRIGGER:",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = LevelInfoAccent
                            )
                            Text(
                                text = nextSyncTime,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                color = LevelInfoAccent
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "LOCK SCREEN PUSH:",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = RetroTextPrimary
                            )
                            Text(
                                text = "ACTIVE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                color = LevelInfoAccent
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RetroInverseBg)
                        .border(2.dp, RetroBlack)
                        .clickable { onDismiss() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[CONFIRM // CLOSE]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = RetroInverseText
                    )
                }
            }
        }
    }
}
