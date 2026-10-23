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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * Dedicated Full Screen Email Reader.
 * - Renders web emails and HTML tags just like the Gmail app.
 * - Styles all hyperlinks in light blue (#1E88E5 in Day, #64B5F6 in Night) with underline.
 * - Full Dark Mode support: calm charcoal background with zero eye strain at night.
 * - When tapped, links take the user directly to the destination in browser / native app.
 * - Cleans out ugly MSO / VML / conditional comment tags.
 * - Provides seamless switching between [GMAIL WEB VIEW] and [RAW TEXT].
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
        // Retro Window Title Bar
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
                    text = "DOC_VIEWER // ID:${email.id.take(8).uppercase()}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
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
                        .padding(vertical = 6.dp),
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
                        .padding(vertical = 6.dp),
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

        // Main Viewer Body
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isWebViewMode) {
                // Gmail-style Rich Web View (WebView with styled light blue links and tag rendering)
                EmailWebView(
                    email = email,
                    summary = summary,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Monospace Raw Text View with clickable light blue links
                EmailRawTextView(
                    email = email,
                    summary = summary,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom Action Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(RetroCreamSurface)
                .border(2.dp, RetroBlack)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Copy Button
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
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "[COPY]",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = RetroTextPrimary
                        )
                    }
                }

                // Open in Gmail Button
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .background(RetroInverseBg)
                        .border(2.dp, RetroBlack)
                        .clickable {
                            val gmailUrl = "https://mail.google.com/mail/u/0/#all/${email.id}"
                            openExternalUrl(context, gmailUrl)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            tint = RetroInverseText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "> GMAIL LINK",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = RetroInverseText
                        )
                    }
                }
            }
        }
    }
}

/**
 * Gmail-style WebView for rich HTML rendering, styling links in light blue,
 * and intercepting clicks to open external links in browser / native apps.
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
                    javaScriptEnabled = false // Safety for emails
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
 * Monospace fallback text view with linkified light blue URLs and click handlers.
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

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Metadata Card
        RetroBox(
            backgroundColor = RetroWhite,
            shadowOffset = 3.dp,
            cornerRadius = 4.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "FROM: ${email.sender}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RetroTextPrimary
                )
                if (email.date.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "DATE: ${email.date}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = RetroMediumGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Subject Header
        Text(
            text = email.subject.ifEmpty { "(NO_SUBJECT)" },
            fontFamily = FontFamily.Monospace,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = RetroTextPrimary,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // AI Digest Box
        if (!summary.isNullOrBlank()) {
            RetroBox(
                backgroundColor = RetroCreamSurface,
                shadowOffset = 3.dp,
                cornerRadius = 4.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(RetroInverseBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "AI_DIGEST // SUMMARY",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = RetroInverseText
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
 * Builds full HTML document styled like Gmail with retro headers,
 * light blue links (#1E88E5 in Day, #64B5F6 in Night), responsive layouts,
 * stripped MSO tags, and dark mode palette support.
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

        // Pattern: &lt;https://...&gt;Link Text
        formatted = formatted.replace(Regex("&lt;(https?://[^&]+)&gt;\\s*([^&<\n]+)")) { match ->
            val url = match.groupValues[1].trim()
            val label = match.groupValues[2].trim()
            """<a href="$url">$label</a>"""
        }
        // Pattern: &lt;https://...&gt;
        formatted = formatted.replace(Regex("&lt;(https?://[^&]+)&gt;")) { match ->
            val url = match.groupValues[1].trim()
            """<a href="$url">$url</a>"""
        }
        // Pattern: Standalone URLs
        formatted = formatted.replace(Regex("(?<!href=\")(?<!\">)(https?://[a-zA-Z0-9_.\\-~:/?#\\[\\]@!$&'()*+,;=%]+)")) { match ->
            val url = match.groupValues[1]
            """<a href="$url">$url</a>"""
        }
        // Newlines to <br>
        formatted.replace("\n", "<br>")
    }

    val summaryHtml = if (!summary.isNullOrBlank()) {
        """
        <div class="summary-box">
            <div class="summary-tag">AI_DIGEST // SUMMARY</div>
            <div class="summary-text">${escapeHtmlSpecialChars(summary)}</div>
        </div>
        """.trimIndent()
    } else ""

    val dateHtml = if (email.date.isNotEmpty()) {
        """<div class="meta-date">DATE: ${escapeHtmlSpecialChars(email.date)}</div>"""
    } else ""

    // Dynamic color values for HTML rendering (Dark Black background with Cream borders in Night Mode)
    val pageBg = if (isDark) "#0A0A0C" else "#F5F1EB"
    val textPrimary = if (isDark) "#F5F1EB" else "#0F0F0F"
    val cardBg = if (isDark) "#141418" else "#FFFFFF"
    val cardBorder = if (isDark) "#F5F1EB" else "#0F0F0F"
    val dropShadow = if (isDark) "#26262B" else "#0F0F0F"
    val metaDate = if (isDark) "#D4CEC3" else "#5A5A5A"
    val summaryBoxBg = if (isDark) "#18181D" else "#EBE6DC"
    val summaryTagBg = if (isDark) "#F5F1EB" else "#0F0F0F"
    val summaryTagColor = if (isDark) "#0A0A0C" else "#FFFFFF"
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
      /* Retro Document Metadata Card */
      .retro-meta-card {
        background-color: $cardBg;
        border: 2px solid $cardBorder;
        box-shadow: 3px 3px 0px $dropShadow;
        border-radius: 4px;
        padding: 12px;
        margin-bottom: 12px;
        font-family: "Courier New", Courier, monospace;
      }
      .meta-sender {
        font-weight: bold;
        font-size: 12px;
        color: $textPrimary;
      }
      .meta-date {
        font-size: 11px;
        color: $metaDate;
        margin-top: 4px;
      }
      .subject-title {
        font-size: 18px;
        font-weight: 900;
        margin: 12px 0 10px 0;
        line-height: 1.3;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        color: $textPrimary;
      }
      /* AI Digest Box */
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
        background-color: $summaryTagBg;
        color: $summaryTagColor;
        font-family: "Courier New", Courier, monospace;
        font-size: 10px;
        font-weight: 900;
        padding: 2px 6px;
        margin-bottom: 8px;
      }
      .summary-text {
        font-size: 13px;
        color: $summaryTextColor;
        line-height: 1.5;
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
        <div class="meta-sender">FROM: ${escapeHtmlSpecialChars(email.sender)}</div>
        $dateHtml
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
