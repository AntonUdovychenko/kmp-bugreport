package io.github.antonudovychenko.bugreport.logs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * A process-local, size-bounded history of log lines, meant to be attached to a bug report.
 *
 * Feed it from your logger (Kermit, Napier, your own) with [append] and pass `{ buffer.export() }`
 * as the `logs` source of [io.github.antonudovychenko.bugreport.BugReportHost].
 *
 * Values that commonly carry credentials or personal data — bearer tokens, JWTs, e-mail addresses,
 * signed-URL parameters, `password=`/`token=` pairs — are redacted **before** they enter the buffer,
 * so nothing sensitive is ever held in memory waiting to be sent. Structured attributes are left out
 * on purpose: they too often hold whole request payloads.
 *
 * Thread-safe: appends from any thread are applied atomically.
 *
 * @param capacityBytes upper bound for the UTF-8 size of everything retained; the oldest lines are
 *   dropped first.
 * @param redact applied to every line before it is stored. Defaults to [redactDiagnosticText];
 *   compose it with your own rules for app-specific secrets.
 */
public class DiagnosticLogBuffer(
    private val capacityBytes: Int = DefaultCapacityBytes,
    private val redact: (String) -> String = ::redactDiagnosticText,
) {
    private val lines = MutableStateFlow<List<ByteArray>>(emptyList())

    /** Records one line, formatted as `LEVEL [tag] message | ThrowableType: throwable message`. */
    public fun append(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (capacityBytes <= 0) return

        val line = buildString {
            append(level)
            append(" [")
            append(tag)
            append("] ")
            append(message)
            throwable?.let {
                append(" | ")
                append(it::class.simpleName ?: "Throwable")
                it.message?.let { throwableMessage ->
                    append(": ")
                    append(throwableMessage)
                }
            }
        }
            .take(MaxLineCharacters)
            .let(redact)
            .plus("\n")
            .encodeToByteArray()
            .let { if (it.size <= capacityBytes) it else it.copyOf(capacityBytes) }

        lines.update { current ->
            val retained = current.toMutableList()
            retained.add(line)
            var retainedBytes = retained.sumOf(ByteArray::size)
            while (retained.size > 1 && retainedBytes > capacityBytes) {
                retainedBytes -= retained.removeAt(0).size
            }
            retained
        }
    }

    /** The newest lines that fit in [maxBytes], oldest first, as UTF-8 bytes. */
    public fun exportBytes(maxBytes: Int = capacityBytes): ByteArray {
        if (maxBytes <= 0) return byteArrayOf()
        val snapshot = lines.value
        val selected = ArrayDeque<ByteArray>()
        var selectedBytes = 0

        for (line in snapshot.asReversed()) {
            if (selectedBytes + line.size > maxBytes) break
            selected.addFirst(line)
            selectedBytes += line.size
        }

        val output = ByteArray(selectedBytes)
        var offset = 0
        selected.forEach { line ->
            line.copyInto(output, destinationOffset = offset)
            offset += line.size
        }
        return output
    }

    /** The newest lines that fit in [maxBytes] as text, or `null` when nothing was logged yet. */
    public fun export(maxBytes: Int = capacityBytes): String? =
        exportBytes(maxBytes).takeIf(ByteArray::isNotEmpty)?.decodeToString()

    /** Drops everything recorded so far. */
    public fun clear() {
        lines.value = emptyList()
    }

    public companion object {
        /** 1 MiB — a few thousand lines, comfortably under typical upload limits. */
        public const val DefaultCapacityBytes: Int = 1024 * 1024
        private const val MaxLineCharacters: Int = 8 * 1024
    }
}

/**
 * Masks bearer tokens, JWTs, e-mail addresses, credential-looking query parameters and
 * `token=`/`password=`/`secret=`/`authorization=`/`api_key=` pairs in [value].
 */
public fun redactDiagnosticText(value: String): String {
    var sanitized = value
    redactionRules.forEach { rule ->
        sanitized = rule.regex.replace(sanitized) { match ->
            rule.replacement(match)
        }
    }
    return sanitized
}

private class RedactionRule(
    val regex: Regex,
    val replacement: (MatchResult) -> String,
)

private val redactionRules = listOf(
    RedactionRule(
        regex = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+"""),
        replacement = { "Bearer [REDACTED]" },
    ),
    RedactionRule(
        regex = Regex("""\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b"""),
        replacement = { "[REDACTED_JWT]" },
    ),
    RedactionRule(
        regex = Regex("""(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b"""),
        replacement = { "[REDACTED_EMAIL]" },
    ),
    RedactionRule(
        regex = Regex(
            """(?i)([?&](?:token|signature|key|auth|code|jwt|access_token|refresh_token)=)[^&#\s]+"""
        ),
        replacement = { match -> "${match.groupValues[1]}[REDACTED]" },
    ),
    RedactionRule(
        regex = Regex(
            """(?i)\b(token|password|secret|authorization|api[_-]?key)\s*[:=]\s*["']?[^"',;\s]+"""
        ),
        replacement = { match -> "${match.groupValues[1]}=[REDACTED]" },
    ),
)
