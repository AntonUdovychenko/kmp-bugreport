package io.github.antonudovychenko.bugreport.ktor

import io.github.antonudovychenko.bugreport.BugReport
import io.github.antonudovychenko.bugreport.BugReportSender
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.http.isSuccess

/**
 * Names of the parts in the multipart body. A `null` name leaves that part out.
 *
 * The defaults produce:
 * - `description` — the Markdown description
 * - `device` — e.g. `iOS 18.2 (iPhone iPhone16,2)`
 * - one form field per [BugReport.metadata] entry, named by its key
 * - one `screenshot` file part per image (`image/jpeg`, `screenshot_<n>.jpg`)
 * - `logs` — a `text/plain` file part named [logsFileName], when the report carries logs
 */
public class MultipartBugReportFormat(
    public val descriptionField: String = "description",
    public val plainTextDescriptionField: String? = null,
    public val deviceField: String? = "device",
    public val imageField: String? = "screenshot",
    public val logsField: String? = "logs",
    public val logsFileName: String = "logs.txt",
)

/** A report endpoint answered with a non-2xx status. */
public class BugReportHttpException(
    public val statusCode: Int,
    public val responseBody: String,
) : Exception("Bug report upload failed with HTTP $statusCode")

/** The report as multipart form parts, for use with any Ktor form API. */
public fun BugReport.toFormParts(format: MultipartBugReportFormat = MultipartBugReportFormat()): List<PartData> =
    formData {
        append(format.descriptionField, description)
        format.plainTextDescriptionField?.let { append(it, plainTextDescription) }
        format.deviceField?.let { append(it, device.toString()) }
        metadata.forEach { (key, value) -> append(key, value) }
        format.imageField?.let { field ->
            images.filter { it.bytes.isNotEmpty() }.forEach { image ->
                // Repeating the field name is how multipart carries a list; servers collect every
                // part with that name.
                append(field, image.bytes, Headers.build {
                    append(HttpHeaders.ContentType, image.mimeType)
                    append(HttpHeaders.ContentDisposition, "filename=\"${image.fileName}\"")
                })
            }
        }
        val logText = logs
        if (format.logsField != null && !logText.isNullOrEmpty()) {
            append(format.logsField, logText.encodeToByteArray(), Headers.build {
                append(HttpHeaders.ContentType, "text/plain; charset=utf-8")
                append(HttpHeaders.ContentDisposition, "filename=\"${format.logsFileName}\"")
            })
        }
    }

/**
 * POSTs [report] to [url] as `multipart/form-data` and returns the response.
 *
 * @param configure adds headers (auth), timeouts, ... to the request.
 * @throws BugReportHttpException when the server answers with a non-2xx status.
 */
public suspend fun HttpClient.submitBugReport(
    url: String,
    report: BugReport,
    format: MultipartBugReportFormat = MultipartBugReportFormat(),
    configure: HttpRequestBuilder.() -> Unit = {},
): HttpResponse {
    val response = post(url) {
        configure()
        setBody(MultiPartFormDataContent(report.toFormParts(format)))
    }
    if (!response.status.isSuccess()) {
        throw BugReportHttpException(response.status.value, runCatching { response.bodyAsText() }.getOrDefault(""))
    }
    return response
}

/**
 * A [BugReportSender] that uploads every report to [url] with [client].
 *
 * Needs a dynamic URL or a client that is only available asynchronously? Implement
 * [BugReportSender] directly and call [submitBugReport] from it.
 */
public class KtorBugReportSender(
    private val client: HttpClient,
    private val url: String,
    private val format: MultipartBugReportFormat = MultipartBugReportFormat(),
    private val configure: HttpRequestBuilder.() -> Unit = {},
) : BugReportSender {
    override suspend fun send(report: BugReport) {
        client.submitBugReport(url, report, format, configure)
    }
}
