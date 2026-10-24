package com.preetham.aimailassistant.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.preetham.aimailassistant.data.EmailDetail
import com.preetham.aimailassistant.ui.components.RetroBox
import com.preetham.aimailassistant.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Executive Briefing Email Reader View.
 * - Monogram Sender Avatar with category-tinted badge.
 * - Human-readable executive formatted timestamp (e.g. 23 Oct 2026 • 02:13 PM).
 * - Styled hyperlinks in light blue (#1E88E5 in Day, #64B5F6 in Night).
 * - Executive Briefing Summary Card with tactile accent stripe.
 * - Prominent sticky "OPEN IN GMAIL APP" action button with direct package intent.
 */
@Composable
fun EmailDetailScreen(
    email: EmailDetail,
    summary: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isWebViewMode by remember { mutableStateOf(true) }

    // Intercept hardware Android back gesture/button to return smoothly
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroCreamBg)
            .statusBarsPadding()
    ) {
        // Retro Executive Window Title Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetroCreamSurface)
                .border(2.dp, RetroBlack)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                Box(
                    modifier = Modifier
                        .background(RetroWhite)
                        .border(2.dp, RetroBlack)
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = RetroTextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "[BACK]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RetroTextPrimary
                        )
                    }
                }

                // Window Title
                Text(
                    text = "EXECUTIVE_READER // ID:${email.id.take(8).uppercase()}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    color = RetroTextPrimary
                )

                // Close Button
                Box(
                    modifier = Modifier
                        .background(RetroWhite)
                        .border(2.dp, RetroBlack)
                        .clickable { onBack() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
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
        }

        // View Mode Switcher: [GMAIL WEB VIEW] vs [RAW TEXT]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetroCreamBg)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // GMAIL WEB VIEW button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isWebViewMode) RetroInverseBg else RetroWhite)
                        .border(1.5.dp, RetroBlack)
                        .clickable { isWebViewMode = true }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = if (isWebViewMode) RetroInverseText else RetroTextPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "[GMAIL WEB VIEW]",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            color = if (isWebViewMode) RetroInverseText else RetroTextPrimary
                        )
                    }
                }

                // RAW TEXT button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (!isWebViewMode) RetroInverseBg else RetroWhite)
                        .border(1.5.dp, RetroBlack)
                        .clickable { isWebViewMode = false }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = if (!isWebViewMode) RetroInverseText else RetroTextPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "[RAW TEXT]",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            color = if (!isWebViewMode) RetroInverseText else RetroTextPrimary
                        )
                    }
                }
            }
        }

        // Main Reader Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isWebViewMode) {
                EmailWebView(
                    email = email,
                    summary = summary,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                EmailRawTextView(
                    email = email,
                    summary = summary,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Prominent Floating/Sticky Bottom Action Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(RetroCreamSurface)
                .border(2.dp, RetroBlack)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copy Button (secondary action)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(RetroWhite)
                        .border(2.dp, RetroBlack)
                        .clickable {
                            val cleanPayload = cleanEmailBodyForPlainView(email.body)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Email Content", cleanPayload)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "COPIED TO BUFFER", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = RetroTextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "[COPY]",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = RetroTextPrimary
                        )
                    }
                }

                // Prominent "OPEN IN GMAIL APP" Action Button (primary executive action)
                Box(
                    modifier = Modifier
                        .weight(1.6f)
                        .background(RetroInverseBg)
                        .border(2.dp, RetroBlack)
                        .clickable {
                            openInGmailApp(context, email.id)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = RetroInverseText,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "> OPEN IN GMAIL APP",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.5.sp,
                            color = RetroInverseText,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Gmail-style WebView for executive email reading:
 * - Clean sender avatar squircle with company/sender initial
 * - Readable body typography
 * - Formatted timestamp
 * - Light blue hyperlinks (#1E88E5 / #64B5F6) with underline
 * - External link interceptor
 */
@Composable
fun EmailWebView(
    email: EmailDetail,
    summary: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val styledHtml = remember(email, summary, isDark) {
        buildStyledHtml(email, summary, isDark)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    defaultTextEncodingName = "utf-8"
                }
                val bgHex = if (isDark) "#0A0A0C" else "#F5F1EB"
                setBackgroundColor(android.graphics.Color.parseColor(bgHex))

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        return openExternalUrl(context, url)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url.isNullOrEmpty()) return false
                        return openExternalUrl(context, url)
                    }
                }
            }
        },
        update = { webView ->
            val bgHex = if (isDark) "#0A0A0C" else "#F5F1EB"
            webView.setBackgroundColor(android.graphics.Color.parseColor(bgHex))
            webView.loadDataWithBaseURL("https://mail.google.com/", styledHtml, "text/html", "utf-8", null)
        }
    )
}

/**
 * Monospace fallback text view with executive header card and linkified light blue URLs.
 */
@Composable
fun EmailRawTextView(
    email: EmailDetail,
    summary: String?,
    modifier: Modifier = Modifier
) {
    val cleanBody = remember(email.body) {
        cleanEmailBodyForPlainView(email.body)
    }
    val displayName = remember(email.sender) { getSenderDisplayName(email.sender) }
    val senderAddress = remember(email.sender) { getSenderAddress(email.sender) }
    val initial = remember(email.sender) { getSenderInitial(email.sender) }
    val formattedDate = remember(email.date) { formatEmailDate(email.date) }

    val isSecurity = email.subject.contains("password", ignoreCase = true) ||
                     email.subject.contains("security", ignoreCase = true) ||
                     email.subject.contains("verification", ignoreCase = true)
    val isAction = email.subject.contains("invitation", ignoreCase = true) ||
                   email.subject.contains("connect", ignoreCase = true) ||
                   email.body.contains("waiting for your response", ignoreCase = true)
    val isImportant = email.subject.contains("webinar", ignoreCase = true) ||
                      email.subject.contains("exam", ignoreCase = true) ||
                      email.subject.contains("cert", ignoreCase = true)

    val accentColor = when {
        isSecurity -> LevelSecurityAccent
        isAction -> LevelActionAccent
        isImportant -> LevelImportantAccent
        else -> LevelInfoAccent
    }
    val badgeBg = when {
        isSecurity -> LevelSecurityBg
        isAction -> LevelActionBg
        isImportant -> LevelImportantBg
        else -> LevelInfoBg
    }
    val badgeText = when {
        isSecurity -> LevelSecurityText
        isAction -> LevelActionText
        isImportant -> LevelImportantText
        else -> LevelInfoText
    }
    val priorityTag = when {
        isSecurity -> "[SECURITY_ALERT]"
        isAction -> "[ACTION_REQUIRED]"
        isImportant -> "[IMPORTANT]"
        else -> "[INFO_TOPIC]"
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Executive Sender & Metadata Card
        RetroBox(
            backgroundColor = RetroWhite,
            borderColor = RetroBlack,
            shadowColor = RetroBlack,
            shadowOffset = 3.dp,
            cornerRadius = 4.dp,
            leftStripeColor = accentColor
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sender Avatar Squircle
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(badgeBg)
                            .border(1.5.dp, accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayName,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Black,
                                color = RetroTextPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .background(badgeBg)
                                    .border(1.dp, accentColor)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = priorityTag,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = badgeText
                                )
                            }
                        }

                        if (senderAddress.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = senderAddress,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                color = RetroMediumGray
                            )
                        }
                    }
                }

                if (formattedDate.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = RetroMediumGray,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = formattedDate,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = RetroMediumGray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Subject Header
        Text(
            text = email.subject.ifEmpty { "(NO_SUBJECT)" },
            fontFamily = FontFamily.Monospace,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            color = RetroTextPrimary,
            lineHeight = 23.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Executive AI Summary Box
        if (!summary.isNullOrBlank()) {
            RetroBox(
                backgroundColor = RetroCreamSurface,
                shadowOffset = 3.dp,
                cornerRadius = 4.dp,
                leftStripeColor = accentColor
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(badgeBg)
                                .border(1.dp, accentColor)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "✦ AI_EXECUTIVE_SUMMARY",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = badgeText
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = summary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = RetroTextPrimary,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Transmission Header
        Text(
            text = "=== [RAW MONOSPACE TRANSMISSION] ===",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = RetroDarkGray,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Linkified text card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(RetroWhite)
                .border(2.dp, RetroBlack)
                .padding(14.dp)
        ) {
            RetroClickableMonospaceText(
                text = cleanBody.ifEmpty { "(NO_RAW_PAYLOAD)" }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Parses URLs in monospace text and makes them clickable light blue links.
 */
@Composable
fun RetroClickableMonospaceText(
    text: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val linkColor = RetroLinkLightBlue
    val urlRegex = remember { Regex("(https?://[a-zA-Z0-9_.\\-~:/?#\\[\\]@!$&'()*+,;=%]+)") }

    val annotatedString = remember(text, linkColor) {
        buildAnnotatedString {
            var lastIndex = 0
            for (match in urlRegex.findAll(text)) {
                val start = match.range.first
                val end = match.range.last + 1
                if (start > lastIndex) {
                    append(text.substring(lastIndex, start))
                }
                val url = match.value
                pushStringAnnotation(tag = "URL", annotation = url)
                pushStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold
                    )
                )
                append(url)
                pop()
                pop()
                lastIndex = end
            }
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = RetroTextPrimary,
            lineHeight = 19.sp
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    openExternalUrl(context, annotation.item)
                }
        }
    )
}

/**
 * Builds HTML document styled like Gmail with executive metadata card,
 * sender avatar monogram, formatted timestamp, light blue links (#1E88E5 / #64B5F6),
 * responsive typography, and dark mode palette support.
 */
fun buildStyledHtml(
    email: EmailDetail,
    summary: String?,
    isDark: Boolean = false
): String {
    val isHtml = email.body.contains("<html", ignoreCase = true) ||
                 email.body.contains("<div", ignoreCase = true) ||
                 email.body.contains("<p>", ignoreCase = true) ||
                 email.body.contains("<table", ignoreCase = true) ||
                 email.body.contains("<br", ignoreCase = true) ||
                 email.body.contains("<a ", ignoreCase = true)

    // Pre-clean MSO conditional comments and VML tags
    var cleaned = email.body
        .replace(Regex("<!--\\[if.*?<!\\[endif\\]-->", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("</?[vw]:[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace("&zwnj;", "")
        .replace("\u200c", "")
        .replace("\u200b", "")
        .replace("\u200a", "")
        .replace("\u200e", "")
        .replace("\u200f", "")

    val bodyHtml = if (isHtml) {
        cleaned
    } else {
        // Plain text emails: transform URLs into clickable HTML links
        var formatted = escapeHtmlSpecialChars(cleaned)

        formatted = formatted.replace(Regex("&lt;(https?://[^&]+)&gt;\\s*([^&<\n]+)")) { match ->
            val url = match.groupValues[1].trim()
            val label = match.groupValues[2].trim()
            """<a href="$url">$label</a>"""
        }
        formatted = formatted.replace(Regex("&lt;(https?://[^&]+)&gt;")) { match ->
            val url = match.groupValues[1].trim()
            """<a href="$url">$url</a>"""
        }
        formatted = formatted.replace(Regex("(?<!href=\")(?<!\">)(https?://[a-zA-Z0-9_.\\-~:/?#\\[\\]@!$&'()*+,;=%]+)")) { match ->
            val url = match.groupValues[1]
            """<a href="$url">$url</a>"""
        }
        formatted.replace("\n", "<br>")
    }

    val displayName = getSenderDisplayName(email.sender)
    val senderAddress = getSenderAddress(email.sender)
    val initial = getSenderInitial(email.sender)
    val formattedDate = formatEmailDate(email.date)

    val isSecurity = email.subject.contains("password", ignoreCase = true) ||
                     email.subject.contains("security", ignoreCase = true) ||
                     email.subject.contains("verification", ignoreCase = true)
    val isAction = email.subject.contains("invitation", ignoreCase = true) ||
                   email.subject.contains("connect", ignoreCase = true) ||
                   email.body.contains("waiting for your response", ignoreCase = true)
    val isImportant = email.subject.contains("webinar", ignoreCase = true) ||
                      email.subject.contains("exam", ignoreCase = true) ||
                      email.subject.contains("cert", ignoreCase = true)

    val levelAccent = when {
        isSecurity -> if (isDark) "#EF5350" else "#C62828"
        isAction -> if (isDark) "#F59E0B" else "#D97706"
        isImportant -> if (isDark) "#60A5FA" else "#2563EB"
        else -> if (isDark) "#4ADE80" else "#16A34A"
    }

    val levelBg = when {
        isSecurity -> if (isDark) "#2B1418" else "#FCE8E6"
        isAction -> if (isDark) "#281B0E" else "#FEF3C7"
        isImportant -> if (isDark) "#101E33" else "#EFF6FF"
        else -> if (isDark) "#0F2618" else "#F0FDF4"
    }

    val levelText = when {
        isSecurity -> if (isDark) "#FF8A80" else "#B71C1C"
        isAction -> if (isDark) "#FDE68A" else "#92400E"
        isImportant -> if (isDark) "#BFDBFE" else "#1E40AF"
        else -> if (isDark) "#BBF7D0" else "#166534"
    }

    val priorityTag = when {
        isSecurity -> "SECURITY_ALERT // CRITICAL"
        isAction -> "ACTION_REQUIRED // ATTENTION"
        isImportant -> "SYSTEM_NOTICE // IMPORTANT"
        else -> "INFORMATIONAL // TOPIC"
    }

    val summaryHtml = if (!summary.isNullOrBlank()) {
        """
        <div class="summary-box" style="border-left: 5px solid $levelAccent;">
            <div class="summary-tag" style="background-color: $levelBg; color: $levelText; border: 1.5px solid $levelAccent;">✦ AI_EXECUTIVE_SUMMARY // $priorityTag</div>
            <div class="summary-text">${escapeHtmlSpecialChars(summary)}</div>
        </div>
        """.trimIndent()
    } else ""

    val pageBg = if (isDark) "#0A0A0C" else "#F5F1EB"
    val textPrimary = if (isDark) "#F5F1EB" else "#0F0F0F"
    val cardBg = if (isDark) "#141418" else "#FFFFFF"
    val cardBorder = if (isDark) "#F5F1EB" else "#0F0F0F"
    val dropShadow = if (isDark) "#26262B" else "#0F0F0F"
    val metaDate = if (isDark) "#D4CEC3" else "#5A5A5A"
    val summaryBoxBg = if (isDark) "#18181D" else "#EBE6DC"
    val summaryTextColor = if (isDark) "#F5F1EB" else "#141414"
    val transmissionHeader = if (isDark) "#D4CEC3" else "#2B2B2B"
    val canvasBg = if (isDark) "#141418" else "#FFFFFF"
    val linkColor = if (isDark) "#64B5F6" else "#1E88E5"
    val linkActive = if (isDark) "#90CAF9" else "#1565C0"
    val codeBg = if (isDark) "#18181D" else "#F5F1EB"

    return """
    <!DOCTYPE html>
    <html>
    <head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
    <style>
      * { box-sizing: border-box; }
      body {
        margin: 0;
        padding: 12px 14px 40px 14px;
        background-color: $pageBg;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
        color: $textPrimary;
        font-size: 14px;
        line-height: 1.6;
        word-break: break-word;
        overflow-wrap: break-word;
      }
      /* Executive Metadata Card */
      .retro-meta-card {
        background-color: $cardBg;
        border: 2px solid $cardBorder;
        box-shadow: 3px 3px 0px $dropShadow;
        border-radius: 4px;
        padding: 12px 14px;
        margin-bottom: 12px;
        border-left: 5px solid $levelAccent;
      }
      .avatar-row {
        display: flex;
        align-items: center;
        gap: 12px;
      }
      .avatar-chip {
        width: 38px;
        height: 38px;
        background-color: $levelBg;
        border: 2px solid $levelAccent;
        color: $levelText;
        font-family: "Courier New", Courier, monospace;
        font-size: 17px;
        font-weight: 900;
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 4px;
        flex-shrink: 0;
      }
      .meta-info {
        flex: 1;
        min-width: 0;
      }
      .meta-sender-name {
        font-weight: 800;
        font-size: 14px;
        color: $textPrimary;
        font-family: "Courier New", Courier, monospace;
      }
      .meta-sender-addr {
        font-size: 11px;
        color: $metaDate;
        font-family: "Courier New", Courier, monospace;
        margin-top: 2px;
      }
      .badge-tag {
        display: inline-block;
        background-color: $levelBg;
        color: $levelText;
        border: 1px solid $levelAccent;
        padding: 2px 6px;
        font-size: 9px;
        font-weight: 800;
        font-family: "Courier New", Courier, monospace;
        margin-top: 4px;
      }
      .date-line {
        font-size: 11px;
        color: $metaDate;
        font-family: "Courier New", Courier, monospace;
        font-weight: bold;
        margin-top: 8px;
        border-top: 1px dashed $dropShadow;
        padding-top: 8px;
      }
      .subject-title {
        font-size: 17px;
        font-weight: 900;
        margin: 12px 0 10px 0;
        line-height: 1.35;
        font-family: "Courier New", Courier, monospace;
        color: $textPrimary;
      }
      /* AI Executive Digest Box */
      .summary-box {
        background-color: $summaryBoxBg;
        border: 2px solid $cardBorder;
        box-shadow: 3px 3px 0px $dropShadow;
        border-radius: 4px;
        padding: 12px;
        margin-bottom: 14px;
      }
      .summary-tag {
        display: inline-block;
        font-family: "Courier New", Courier, monospace;
        font-size: 10px;
        font-weight: 900;
        padding: 2px 6px;
        margin-bottom: 8px;
      }
      .summary-text {
        font-size: 13px;
        color: $summaryTextColor;
        line-height: 1.55;
        font-family: "Courier New", Courier, monospace;
      }
      .transmission-header {
        font-family: "Courier New", Courier, monospace;
        font-size: 11px;
        font-weight: bold;
        color: $transmissionHeader;
        letter-spacing: 1px;
        margin: 14px 0 8px 0;
      }
      /* Main Email Canvas (Gmail rendering) */
      .email-canvas {
        background-color: $canvasBg;
        border: 2px solid $cardBorder;
        box-shadow: 3px 3px 0px $dropShadow;
        border-radius: 4px;
        padding: 14px;
        min-height: 200px;
        color: $textPrimary;
        line-height: 1.65;
      }
      /* Gmail Style Hyperlinks */
      a {
        color: $linkColor !important;
        text-decoration: underline !important;
        font-weight: 600;
      }
      a:visited {
        color: $linkColor !important;
      }
      a:active, a:hover {
        color: $linkActive !important;
      }
      img {
        max-width: 100% !important;
        height: auto !important;
        border-radius: 4px;
      }
      table {
        max-width: 100% !important;
        border-collapse: collapse;
      }
      pre, code {
        font-family: "Courier New", Courier, monospace;
        background: $codeBg;
        color: $textPrimary;
        padding: 2px 4px;
        border-radius: 2px;
        white-space: pre-wrap;
      }
    </style>
    </head>
    <body>
      <div class="retro-meta-card">
        <div class="avatar-row">
          <div class="avatar-chip">$initial</div>
          <div class="meta-info">
            <div class="meta-sender-name">${escapeHtmlSpecialChars(displayName)}</div>
            ${if (senderAddress.isNotEmpty()) """<div class="meta-sender-addr">${escapeHtmlSpecialChars(senderAddress)}</div>""" else ""}
            <div class="badge-tag">[$priorityTag]</div>
          </div>
        </div>
        ${if (formattedDate.isNotEmpty()) """<div class="date-line">📅 $formattedDate</div>""" else ""}
      </div>

      <div class="subject-title">${escapeHtmlSpecialChars(email.subject.ifEmpty { "(NO_SUBJECT)" })}</div>

      $summaryHtml

      <div class="transmission-header">=== [MESSAGE TRANSMISSION] ===</div>

      <div class="email-canvas">
        $bodyHtml
      </div>
    </body>
    </html>
    """.trimIndent()
}

fun cleanEmailBodyForPlainView(raw: String): String {
    return raw
        .replace(Regex("<!--\\[if.*?<!\\[endif\\]-->", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("</?[vw]:[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace("&zwnj;", "")
        .replace("\u200c", "")
        .replace("\u200b", "")
        .replace("\u200a", "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

fun escapeHtmlSpecialChars(str: String): String {
    return str
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

fun getSenderDisplayName(raw: String): String {
    val beforeAngle = raw.substringBefore("<").trim().replace("\"", "")
    return if (beforeAngle.isNotEmpty()) beforeAngle else raw
}

fun getSenderAddress(raw: String): String {
    val match = Regex("<(.*?)>").find(raw)
    return match?.groupValues?.get(1)?.trim() ?: ""
}

fun getSenderInitial(raw: String): String {
    val name = getSenderDisplayName(raw)
    return name.firstOrNull { it.isLetter() }?.uppercase() ?: "E"
}

fun formatEmailDate(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val cleaned = raw.replace(Regex("\\s*\\([A-Za-z0-9_]+\\)$"), "").trim()
        val inFormats = listOf(
            SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            SimpleDateFormat("d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
        )
        var parsedDate: java.util.Date? = null
        for (fmt in inFormats) {
            try {
                parsedDate = fmt.parse(cleaned)
                if (parsedDate != null) break
            } catch (_: Exception) {}
        }
        if (parsedDate != null) {
            val outFormat = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.ENGLISH)
            outFormat.format(parsedDate)
        } else {
            raw
        }
    } catch (_: Exception) {
        raw
    }
}

fun openInGmailApp(context: Context, emailId: String) {
    try {
        val gmailIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com/mail/u/0/#all/$emailId")).apply {
            setPackage("com.google.android.gm")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(gmailIntent)
    } catch (e: Exception) {
        // Fallback to browser
        openExternalUrl(context, "https://mail.google.com/mail/u/0/#all/$emailId")
    }
}

fun openExternalUrl(context: Context, rawUrl: String): Boolean {
    var targetUrl = rawUrl.trim()
    if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://") && !targetUrl.startsWith("mailto:")) {
        targetUrl = "https://$targetUrl"
    }

    return try {
        val uri = Uri.parse(targetUrl)
        val intent = if (targetUrl.startsWith("mailto:")) {
            Intent(Intent.ACTION_SENDTO, uri)
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open link: $targetUrl", Toast.LENGTH_SHORT).show()
        false
    }
}
