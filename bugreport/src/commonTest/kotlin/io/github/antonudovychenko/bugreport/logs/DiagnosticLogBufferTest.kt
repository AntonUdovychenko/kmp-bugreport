package io.github.antonudovychenko.bugreport.logs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticLogBufferTest {
    @Test
    fun exportRedactsCredentialsAndPersonalData() {
        val buffer = DiagnosticLogBuffer()
        buffer.append(
            level = "ERROR",
            tag = "Import",
            message = "Bearer secret.token.value user@example.com " +
                "https://example.com/image?signature=abc123&safe=yes token=top-secret",
        )

        val exported = buffer.export().orEmpty()

        assertFalse("secret.token.value" in exported)
        assertFalse("user@example.com" in exported)
        assertFalse("abc123" in exported)
        assertFalse("top-secret" in exported)
        assertTrue("Bearer [REDACTED]" in exported)
        assertTrue("[REDACTED_EMAIL]" in exported)
        assertTrue("safe=yes" in exported)
    }

    @Test
    fun bufferAndExportStayWithinTheirByteLimits() {
        val buffer = DiagnosticLogBuffer(capacityBytes = 96)
        repeat(20) { index ->
            buffer.append("INFO", "Import", "diagnostic-line-$index")
        }

        assertTrue(buffer.exportBytes().size <= 96)
        assertTrue(buffer.exportBytes(maxBytes = 48).size <= 48)
        assertTrue("diagnostic-line-19" in buffer.export().orEmpty())
        assertFalse("diagnostic-line-0" in buffer.export().orEmpty())
    }

    @Test
    fun throwableTypeAndMessageAreRecordedAndRedacted() {
        val buffer = DiagnosticLogBuffer()
        buffer.append("WARN", "Sync", "push failed", IllegalStateException("password=hunter2"))

        assertEquals("WARN [Sync] push failed | IllegalStateException: password=[REDACTED]\n", buffer.export())
    }

    @Test
    fun customRedactionRunsInsteadOfTheDefault() {
        val buffer = DiagnosticLogBuffer(redact = { redactDiagnosticText(it).replace("VIN123", "[VIN]") })
        buffer.append("INFO", "Car", "VIN123 for user@example.com")

        assertEquals("INFO [Car] [VIN] for [REDACTED_EMAIL]\n", buffer.export())
    }

    @Test
    fun emptyBufferExportsNullAndClearEmptiesIt() {
        val buffer = DiagnosticLogBuffer()
        assertNull(buffer.export())
        buffer.append("INFO", "A", "line")
        buffer.clear()
        assertNull(buffer.export())
    }
}
