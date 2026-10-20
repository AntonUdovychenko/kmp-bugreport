package io.github.antonudovychenko.bugreport.ktor

import io.github.antonudovychenko.bugreport.BugReport
import io.github.antonudovychenko.bugreport.BugReportImage
import io.github.antonudovychenko.bugreport.BugReportPlatform
import io.github.antonudovychenko.bugreport.DeviceInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KtorBugReportSenderTest {
    private val report = BugReport(
        description = "**Save** fails",
        plainTextDescription = "Save fails",
        images = listOf(
            BugReportImage(byteArrayOf(1, 2, 3), "screenshot_0.jpg"),
            BugReportImage(byteArrayOf(4, 5), "screenshot_1.jpg"),
        ),
        logs = "INFO [App] started\n",
        device = DeviceInfo(BugReportPlatform.Android, "Android", "15 (API 35)", "Google Pixel 9"),
        metadata = mapOf("platform" to "android", "buildVersion" to "1.2.3"),
    )

    @Test
    fun postsEveryPartOfTheReport() = runTest {
        var captured: HttpRequestData? = null
        var body = ""
        val client = HttpClient(MockEngine { request ->
            captured = request
            body = request.body.toByteArray().decodeToString()
            respond("", HttpStatusCode.Created)
        })

        KtorBugReportSender(client, "https://api.example.com/feedback") { header("Authorization", "Bearer t") }
            .send(report)

        val request = captured!!
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.example.com/feedback", request.url.toString())
        assertEquals("Bearer t", request.headers["Authorization"])
        assertTrue(request.body.contentType.toString().startsWith("multipart/form-data"))
        assertPart(body, "description", "**Save** fails")
        assertPart(body, "device", "Android 15 (API 35) (Google Pixel 9)")
        assertPart(body, "platform", "android")
        assertPart(body, "buildVersion", "1.2.3")
        assertEquals(2, Regex("""[;\s]name="?screenshot"?[;\r]""").findAll(body).count())
        assertTrue("filename=\"screenshot_1.jpg\"" in body)
        assertTrue("filename=\"logs.txt\"" in body)
        assertTrue("INFO [App] started" in body)
    }

    @Test
    fun aNullFieldNameLeavesThePartOut() = runTest {
        var body = ""
        val client = HttpClient(MockEngine { request ->
            body = request.body.toByteArray().decodeToString()
            respond("", HttpStatusCode.OK)
        })
        val format = MultipartBugReportFormat(deviceField = null, logsField = null, logsFileName = "app.log")

        client.submitBugReport("https://api.example.com/r", report, format)

        assertFalse("name=\"device\"" in body || "name=device" in body)
        assertFalse("INFO [App] started" in body)
    }

    @Test
    fun aNon2xxResponseThrowsWithTheStatus() = runTest {
        val client = HttpClient(MockEngine { respond("slow down", HttpStatusCode.TooManyRequests) })

        val failure = assertFailsWith<BugReportHttpException> {
            KtorBugReportSender(client, "https://api.example.com/feedback").send(report)
        }

        assertEquals(429, failure.statusCode)
        assertEquals("slow down", failure.responseBody)
    }

    private fun assertPart(body: String, name: String, value: String) {
        val pattern = Regex("""name="?${Regex.escape(name)}"?\r\n(?:[^\r\n]+\r\n)*\r\n${Regex.escape(value)}\r\n""")
        assertTrue(pattern.containsMatchIn(body), "part $name=$value not found in:\n$body")
    }
}
