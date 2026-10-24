package com.preetham.aimailassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preetham.aimailassistant.data.AccountMeta
import com.preetham.aimailassistant.ui.theme.*

/**
 * Interactive Retro Console Account Switcher Header & Quick-Filter Strip.
 * Renders [ACC: ALL ▾] or [ACC: WORK ▾] alongside tactile quick-select account pills.
 */
@Composable
fun AccountSwitcherHeader(
    accounts: List<AccountMeta>,
    selectedAccountId: String?,
    onSelectAccount: (String?) -> Unit,
    onOpenAccountManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedLabel = remember(selectedAccountId, accounts) {
        if (selectedAccountId == null || selectedAccountId == "ALL") {
            "ALL"
        } else {
            accounts.firstOrNull { it.id == selectedAccountId }?.label?.uppercase() ?: "ALL"
        }
    }

    val selectedColorHex = remember(selectedAccountId, accounts) {
        if (selectedAccountId == null || selectedAccountId == "ALL") {
            "#3B82F6"
        } else {
            accounts.firstOrNull { it.id == selectedAccountId }?.color_hex ?: "#3B82F6"
        }
    }

    val parsedAccentColor = remember(selectedColorHex) {
        try {
            Color(android.graphics.Color.parseColor(selectedColorHex))
        } catch (_: Exception) {
            Color(0xFF3B82F6)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(RetroCreamSurface)
            .border(1.dp, RetroBlack)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main Interactive Console Pill: [SYS.BRIEFING_OS // ACC: ALL ▾]
            Box(
                modifier = Modifier
                    .background(RetroWhite)
                    .border(1.5.dp, RetroBlack)
                    .clickable { onOpenAccountManager() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(parsedAccentColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ACC: $selectedLabel",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = RetroTextPrimary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Switch Account",
                        tint = RetroTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Quick button to open full Account Manager
            Box(
                modifier = Modifier
                    .background(RetroWhite)
                    .border(1.5.dp, RetroBlack)
                    .clickable { onOpenAccountManager() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ManageAccounts,
                        contentDescription = "Manage Accounts",
                        tint = RetroTextPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "[VAULT (${accounts.size})]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RetroTextPrimary
                    )
                }
            }
        }

        // Horizontal Quick Switcher Carousel
        if (accounts.size > 1) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ALL Accounts Pill
                val isAllSelected = selectedAccountId == null || selectedAccountId == "ALL"
                Box(
                    modifier = Modifier
                        .background(if (isAllSelected) RetroInverseBg else RetroWhite)
                        .border(1.2.dp, RetroBlack)
                        .clickable { onSelectAccount(null) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "[ALL]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isAllSelected) RetroInverseText else RetroTextPrimary
                    )
                }

                // Per-Account Quick Pills
                accounts.forEach { acc ->
                    val isSelected = acc.id == selectedAccountId
                    val accColor = try {
                        Color(android.graphics.Color.parseColor(acc.color_hex))
                    } catch (_: Exception) {
                        Color(0xFF3B82F6)
                    }

                    Box(
                        modifier = Modifier
                            .background(if (isSelected) accColor else RetroWhite)
                            .border(1.2.dp, if (isSelected) RetroBlack else accColor)
                            .clickable { onSelectAccount(acc.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(accColor)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                text = "[${acc.label.uppercase()}]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isSelected) RetroWhite else accColor
                            )
                        }
                    }
                }
            }
        }
    }
}
