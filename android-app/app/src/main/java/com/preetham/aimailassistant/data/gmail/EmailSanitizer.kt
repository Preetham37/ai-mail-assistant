package com.preetham.aimailassistant.data.gmail

import org.jsoup.Jsoup
import java.util.regex.Pattern

object EmailSanitizer {

    val HTML_STUB_PHRASES = listOf(
        "html-compatible",
        "html compatible",
        "view this email in",
        "open this email in",
        "open in html",
        "please view in html",
        "enable html",
        "viewing this message in plain text",
        "requires an html email client"
    )

    private val WHITESPACE_PATTERN = Pattern.compile("\\s+")

    /**
     * Strips dangerous and non-content tags (<script>, <style>, <head>, <meta>, <link>, <iframe>),
     * injects spacing after block elements to prevent words running together,
     * and normalizes whitespace into single spaces.
     */
    fun cleanHtml(html: String): String {
        if (html.isBlank()) return ""
        val doc = Jsoup.parse(html)
        doc.select("script, style, head, meta, noscript, link, iframe").remove()

        for (block in doc.select("br, p, div, tr, li, h1, h2, h3, h4, h5, h6")) {
            block.append(" ")
        }

        val text = doc.text()
        return WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim()
    }

    /**
     * Checks if plain-text body is merely an uninformative placeholder instructing the user to view in HTML.
     */
    fun isStubText(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        val lower = text.lowercase()
        return HTML_STUB_PHRASES.any { phrase -> lower.contains(phrase) }
    }

    /**
     * Given plain text and HTML bodies, selects the substantive content.
     * Port of content_gater from gmail.py:
     * 1. If plain text is a stub, discard it and use cleaned HTML.
     * 2. If both exist and HTML body is significantly longer (> 1.3x), prefer cleaned HTML.
     * 3. Otherwise return whichever is available.
     */
    fun resolveSubstantiveBody(plainText: String?, htmlBody: String?): String {
        val cleanedHtml = if (!htmlBody.isNullOrBlank()) cleanHtml(htmlBody) else null
        val cleanPlain = plainText?.trim()

        if (!cleanPlain.isNullOrBlank() && isStubText(cleanPlain) && !cleanedHtml.isNullOrBlank()) {
            return cleanedHtml
        }

        if (!cleanedHtml.isNullOrBlank() && !cleanPlain.isNullOrBlank()) {
            if (cleanedHtml.length > (cleanPlain.length * 1.3)) {
                return cleanedHtml
            }
            return cleanPlain
        }

        return cleanedHtml ?: cleanPlain ?: ""
    }
}
