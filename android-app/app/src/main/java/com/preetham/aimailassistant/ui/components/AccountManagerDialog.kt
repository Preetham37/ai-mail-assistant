package com.preetham.aimailassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.preetham.aimailassistant.data.AccountMeta
import com.preetham.aimailassistant.data.gemini.GeminiApiKeyManager
import com.preetham.aimailassistant.security.OAuthManager
import com.preetham.aimailassistant.security.SecureAccountStorage
import com.preetham.aimailassistant.ui.theme.*

/**
 * Tactile 80s-90s Monospace Modal Dialog for Multi-Account Configuration.
 * Displays Knox-secured inboxes, allows switching active inbox, disconnecting accounts,
 * and onboarding new accounts via simulated or AppAuth OAuth2.
 */
@Composable
fun AccountManagerDialog(
    accounts: List<AccountMeta>,
    selectedAccountId: String?,
    onSelectAccount: (String?) -> Unit,
    onAddAccount: (AccountMeta) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val oauthManager = remember { OAuthManager(context) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var showAddAccountForm by remember { mutableStateOf(false) }
    val storage = remember { SecureAccountStorage(context) }
    val apiKeyManager = remember { GeminiApiKeyManager(storage) }
    var geminiKeyInput by remember { mutableStateOf(apiKeyManager.getApiKey()) }
    var showApiKeyInput by remember { mutableStateOf(false) }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isAuthenticating = true
        oauthManager.handleAuthResponse(result.data) { authResult ->
            isAuthenticating = false
            if (authResult.isSuccess) {
                val newAcc = authResult.getOrThrow()
                onAddAccount(newAcc)
                Toast.makeText(context, "AUTHENTICATED: [${newAcc.label}] ${newAcc.email}", Toast.LENGTH_LONG).show()
            } else {
                val err = authResult.exceptionOrNull()?.message ?: "Authorization failed"
                Toast.makeText(context, "OAuth Error: $err", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        RetroBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            backgroundColor = RetroWhite,
            borderColor = RetroBlack,
            shadowColor = RetroBlack,
            shadowOffset = 5.dp,
            cornerRadius = 6.dp,
            leftStripeColor = LevelInfoAccent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
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
                            text = "CONSOLE://ACCOUNT_MANAGER.SYS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetroTextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(RetroWhite)
                            .border(1.5.dp, RetroBlack)
                            .clickable { onDismiss() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "[X]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetroTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "========================================",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = RetroMediumGray
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONNECTED INBOXES: (${accounts.size}/5)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = RetroTextPrimary
                    )

                    // Unified View Button
                    val isAllSelected = selectedAccountId == null || selectedAccountId == "ALL"
                    Box(
                        modifier = Modifier
                            .background(if (isAllSelected) RetroInverseBg else RetroCreamSurface)
                            .border(1.dp, RetroBlack)
                            .clickable {
                                onSelectAccount(null)
                                onDismiss()
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isAllSelected) "● UNIFIED [ALL]" else "VIEW [ALL]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAllSelected) RetroInverseText else RetroTextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (showAddAccountForm) {
                    AddAccountSubView(
                        onConfirm = { newAccount ->
                            onAddAccount(newAccount)
                            showAddAccountForm = false
                        },
                        onCancel = { showAddAccountForm = false }
                    )
                } else {
                    // List of Connected Accounts
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(accounts) { acc ->
                            val isSelected = acc.id == selectedAccountId
                            AccountEntryCard(
                                account = acc,
                                isSelected = isSelected,
                                onSelect = {
                                    onSelectAccount(acc.id)
                                    onDismiss()
                                },
                                onRemove = { onRemoveAccount(acc.id) },
                                canRemove = accounts.size > 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // [+] ADD GMAIL ACCOUNT Button (Native AppAuth-Android PKCE)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RetroCreamSurface)
                            .border(1.5.dp, RetroBlack)
                            .clickable {
                                try {
                                    oauthManager.startAuthFlow(authLauncher)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "OAuth Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    showAddAccountForm = true
                                }
                            }
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = RetroTextPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isAuthenticating) "[AUTHENTICATING...]" else "[+] ADD GMAIL ACCOUNT",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RetroTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "> INITIATE GOOGLE OAUTH2 PKCE FLOW",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = RetroMediumGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddAccountForm = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "> [OR ENTER PROFILE MANUALLY]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetroMediumGray
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Gemini AI Engine Configuration Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RetroWhite)
                            .border(1.5.dp, RetroBlack)
                            .padding(10.dp)
                    ) {
                        Column {
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
                                            .background(if (apiKeyManager.hasApiKey()) LevelInfoAccent else LevelActionAccent)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "GEMINI AI ENGINE",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = RetroTextPrimary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(if (apiKeyManager.hasApiKey()) LevelInfoAccent else LevelActionAccent)
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (apiKeyManager.hasApiKey()) "READY ●" else "KEY NEEDED ○",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = RetroWhite
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (apiKeyManager.hasApiKey()) "> HARDWARE / BUILD KEY ACTIVE" else "> CONFIGURE GEMINI API KEY FOR ON-DEVICE AI",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = RetroMediumGray
                            )

                            if (showApiKeyInput) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = geminiKeyInput,
                                    onValueChange = { geminiKeyInput = it },
                                    placeholder = {
                                        Text(
                                            text = "AIzaSy...",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = RetroMediumGray
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = RetroWhite,
                                        unfocusedContainerColor = RetroWhite,
                                        focusedBorderColor = RetroBlack,
                                        unfocusedBorderColor = RetroBlack,
                                        focusedTextColor = RetroTextPrimary,
                                        unfocusedTextColor = RetroTextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(RetroWhite)
                                            .border(1.dp, RetroBlack)
                                            .clickable { showApiKeyInput = false }
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "[CANCEL]",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RetroTextPrimary
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(RetroInverseBg)
                                            .border(1.dp, RetroBlack)
                                            .clickable {
                                                apiKeyManager.setApiKey(geminiKeyInput.trim())
                                                showApiKeyInput = false
                                                Toast.makeText(context, "GEMINI KEY SAVED TO KNOX", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "[SAVE KEY]",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = RetroInverseText
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showApiKeyInput = true },
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = "[EDIT / CONFIGURE KEY]",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RetroTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dismiss Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RetroInverseBg)
                            .border(1.5.dp, RetroBlack)
                            .clickable { onDismiss() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[X] DISMISS CONSOLE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            color = RetroInverseText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountEntryCard(
    account: AccountMeta,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean
) {
    val accColor = try {
        Color(android.graphics.Color.parseColor(account.color_hex))
    } catch (_: Exception) {
        Color(0xFF3B82F6)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) RetroCreamSurface else RetroWhite)
            .border(1.5.dp, if (isSelected) accColor else RetroBlack)
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccountBadge(label = account.label, colorHex = account.color_hex)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = account.email.ifBlank { account.display_name.ifBlank { account.id } },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RetroTextPrimary,
                        maxLines = 1
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .background(accColor)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACTIVE ●",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            color = RetroWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "STATUS: ${if (account.is_active) "ACTIVE ●" else "PAUSED ○"} | HARDWARE: KNOX TEE",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                color = RetroMediumGray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Select button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) accColor else RetroWhite)
                        .border(1.dp, RetroBlack)
                        .clickable { onSelect() }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSelected) "[SELECTED]" else "[SELECT]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) RetroWhite else RetroTextPrimary
                    )
                }

                // Disconnect button
                if (canRemove) {
                    Box(
                        modifier = Modifier
                            .background(RetroWhite)
                            .border(1.dp, RetroBlack)
                            .clickable { onRemove() }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Disconnect",
                                tint = RetroBlack,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "[DISCONNECT]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
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
private fun AddAccountSubView(
    onConfirm: (AccountMeta) -> Unit,
    onCancel: () -> Unit
) {
    var emailInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("WORK") }
    var selectedColor by remember { mutableStateOf(SecureAccountStorage.DEFAULT_PALETTES[0]) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RetroCreamSurface)
            .border(1.5.dp, RetroBlack)
            .padding(12.dp)
    ) {
        Text(
            text = ">>> CONFIGURE_NEW_ACCOUNT:",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = RetroBlack
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Email field
        Text(
            text = "GMAIL ADDRESS:",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = RetroMediumGray
        )
        Spacer(modifier = Modifier.height(3.dp))
        OutlinedTextField(
            value = emailInput,
            onValueChange = { emailInput = it },
            placeholder = {
                Text(
                    text = "e.g. user@company.com",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = RetroMediumGray
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = RetroWhite,
                unfocusedContainerColor = RetroWhite,
                focusedBorderColor = RetroBlack,
                unfocusedBorderColor = RetroBlack,
                focusedTextColor = RetroTextPrimary,
                unfocusedTextColor = RetroTextPrimary
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Label selector chips
        Text(
            text = "ACCOUNT LABEL:",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = RetroMediumGray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("WORK", "PERSONAL", "DEV", "PROJECTS").forEach { presetLabel ->
                val isLabelChosen = labelInput.equals(presetLabel, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .background(if (isLabelChosen) RetroInverseBg else RetroWhite)
                        .border(1.dp, RetroBlack)
                        .clickable { labelInput = presetLabel }
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "[$presetLabel]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLabelChosen) RetroInverseText else RetroTextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Color selection
        Text(
            text = "ACCENT COLOR:",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = RetroMediumGray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SecureAccountStorage.DEFAULT_PALETTES.forEach { hex ->
                val c = Color(android.graphics.Color.parseColor(hex))
                val isColorChosen = selectedColor == hex
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(c)
                        .border(if (isColorChosen) 2.dp else 1.dp, RetroBlack)
                        .clickable { selectedColor = hex },
                    contentAlignment = Alignment.Center
                ) {
                    if (isColorChosen) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = RetroWhite,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(RetroWhite)
                    .border(1.5.dp, RetroBlack)
                    .clickable { onCancel() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "[CANCEL]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RetroTextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .background(RetroInverseBg)
                    .border(1.5.dp, RetroBlack)
                    .clickable {
                        val validEmail = emailInput.trim().ifBlank { "account${System.currentTimeMillis() % 1000}@gmail.com" }
                        val id = "acc_" + labelInput.lowercase() + "_" + validEmail.substringBefore("@")
                        val newAccount = AccountMeta(
                            id = id,
                            email = validEmail,
                            display_name = labelInput.uppercase(),
                            label = labelInput.uppercase(),
                            color_hex = selectedColor,
                            is_active = true
                        )
                        onConfirm(newAccount)
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "[SAVE & CONNECT]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = RetroInverseText
                )
            }
        }
    }
}
