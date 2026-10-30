package com.preetham.aimailassistant.data.gmail

import org.junit.Assert.*
import org.junit.Test

class EmailSanitizerTest {

    @Test
    fun `cleanHtml strips dangerous and non-content tags`() {
        val dirtyHtml = """
            <html>
                <head><title>Test Title</title><meta charset="utf-8"></head>
                <style>body { color: red; }</style>
                <body>
                    <script>alert('xss');</script>
                    <noscript>Please enable JS</noscript>
                    <iframe src="http://evil.com"></iframe>
                    <p>Important update regarding your account.</p>
                </body>
            </html>
        """.trimIndent()

        val cleaned = EmailSanitizer.cleanHtml(dirtyHtml)

        assertFalse(cleaned.contains("alert"))
        assertFalse(cleaned.contains("color: red"))
        assertFalse(cleaned.contains("Test Title"))
        assertFalse(cleaned.contains("Please enable JS"))
        assertFalse(cleaned.contains("evil.com"))
        assertEquals("Important update regarding your account.", cleaned)
    }

    @Test
    fun `cleanHtml adds spacing between block elements to avoid run-on text`() {
        val html = "<div>First block</div><div>Second block</div><p>Third paragraph</p><span>Span 1</span><span>Span 2</span>"
        val cleaned = EmailSanitizer.cleanHtml(html)

        assertTrue(cleaned.contains("First block Second block Third paragraph"))
    }

    @Test
    fun `cleanHtml normalizes excessive whitespace`() {
        val html = "<p>   Line 1  \n\n\t   with   spaces   </p>"
        val cleaned = EmailSanitizer.cleanHtml(html)

        assertEquals("Line 1 with spaces", cleaned)
    }

    @Test
    fun `isStubText identifies all known stub phrases`() {
        for (phrase in EmailSanitizer.HTML_STUB_PHRASES) {
            val stubMessage = "Notice: This message requires you to $phrase to view it."
            assertTrue("Expected '$phrase' to be recognized as stub text", EmailSanitizer.isStubText(stubMessage))
        }

        assertTrue(EmailSanitizer.isStubText(""))
        assertTrue(EmailSanitizer.isStubText(null))
        assertFalse(EmailSanitizer.isStubText("Here is your invoice for AWS services totaling $42.10."))
    }

    @Test
    fun `resolveSubstantiveBody prefers cleaned HTML when plain text is a stub`() {
        val plainStub = "Please view this email in an HTML compatible browser."
        val richHtml = "<div><h2>Server Alert</h2><p>CPU usage reached 98% on prod-api-01.</p></div>"

        val resolved = EmailSanitizer.resolveSubstantiveBody(plainStub, richHtml)

        assertTrue(resolved.contains("Server Alert"))
        assertTrue(resolved.contains("CPU usage reached 98% on prod-api-01."))
    }

    @Test
    fun `resolveSubstantiveBody prefers HTML when significantly longer than plain text`() {
        val shortPlain = "Quick meeting note."
        val richHtml = """
            <div>
                <h3>Meeting Agenda</h3>
                <p>1. Review Q3 metrics</p>
                <p>2. Finalize on-device Gemini architecture</p>
                <p>3. Plan deployment to Samsung Galaxy S24</p>
            </div>
        """.trimIndent()

        val resolved = EmailSanitizer.resolveSubstantiveBody(shortPlain, richHtml)

        assertTrue(resolved.contains("Meeting Agenda"))
        assertTrue(resolved.contains("Review Q3 metrics"))
    }

    @Test
    fun `resolveSubstantiveBody returns plain text when HTML is missing or empty`() {
        val plain = "Simple plain text email content."
        val resolved = EmailSanitizer.resolveSubstantiveBody(plain, null)

        assertEquals("Simple plain text email content.", resolved)
    }
}
