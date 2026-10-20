package io.github.antonudovychenko.bugreport

/**
 * Delivers a finished [BugReport] — to your backend, an issue tracker, an email endpoint.
 *
 * The library owns no transport: implement this with whatever client the app already has, or use
 * the ready-made multipart sender in the `bugreport-ktor` artifact.
 */
public fun interface BugReportSender {
    /**
     * Sends [report]. Return normally on success; **throw** on failure. A failure keeps the draft
     * open so the user can retry. Throw [BugReportSendException] to show the user a specific message
     * (rate limited, service unavailable, ...); any other exception shows a generic one.
     */
    public suspend fun send(report: BugReport)
}

/** A send failure whose [userMessage] is shown in the report form as-is. */
public class BugReportSendException(
    public val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause)
