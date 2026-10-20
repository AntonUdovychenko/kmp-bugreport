package io.github.antonudovychenko.bugreport.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.antonudovychenko.bugreport.BugReport
import io.github.antonudovychenko.bugreport.BugReportController
import io.github.antonudovychenko.bugreport.BugReportHost
import io.github.antonudovychenko.bugreport.rememberBugReportController
import io.github.antonudovychenko.bugreport.logs.DiagnosticLogBuffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * A small travel app with a bug in it: "Book" always fails. Report it with the bug icon (or, on iOS
 * and Android 14+, by taking a screenshot), then see what your sender would receive in Inbox.
 */
@Composable
public fun SampleApp(
    darkTheme: Boolean = isSystemInDarkTheme(),
    controller: BugReportController = rememberBugReportController(),
) {
    val logs = remember { DiagnosticLogBuffer() }
    val inbox = remember { mutableStateListOf<BugReport>() }
    LaunchedEffect(Unit) { logs.append("INFO", "App", "Trip Planner started") }

    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        BugReportHost(
            // A real app would upload the report; the sample keeps it to show in Inbox.
            sender = { report ->
                delay(600)
                inbox.add(0, report)
            },
            controller = controller,
            logs = { logs.export() },
            metadata = { mapOf("app" to "Trip Planner sample", "version" to "1.0") },
        ) {
            SampleScaffold(controller = controller, logs = logs, inbox = inbox)
        }
    }
}

private enum class Tab { Trips, Inbox }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleScaffold(controller: BugReportController, logs: DiagnosticLogBuffer, inbox: List<BugReport>) {
    var tab by remember { mutableStateOf(Tab.Trips) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == Tab.Trips) "Trip Planner" else "Received reports") },
                actions = {
                    IconButton(onClick = controller::open) {
                        Icon(Icons.Default.BugReport, contentDescription = "Report a bug")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.Trips,
                    onClick = { tab = Tab.Trips },
                    icon = { Icon(Icons.Default.Luggage, contentDescription = null) },
                    label = { Text("Trips") },
                )
                NavigationBarItem(
                    selected = tab == Tab.Inbox,
                    onClick = { tab = Tab.Inbox },
                    icon = {
                        BadgedBox(badge = { if (inbox.isNotEmpty()) Badge { Text("${inbox.size}") } }) {
                            Icon(Icons.Default.Inbox, contentDescription = null)
                        }
                    },
                    label = { Text("Inbox") },
                )
            }
        },
    ) { padding ->
        when (tab) {
            Tab.Trips -> TripsScreen(padding, logs)
            Tab.Inbox -> InboxScreen(padding, inbox)
        }
    }
}

private class Trip(val city: String, val country: String, val dates: String, val price: String, val colors: List<Color>)

private val Trips = listOf(
    Trip("Lisbon", "Portugal", "12 – 16 May", "€840", listOf(Color(0xFF00897B), Color(0xFF1E88E5))),
    Trip("Kyoto", "Japan", "3 – 11 Apr", "€2,310", listOf(Color(0xFFD81B60), Color(0xFF8E24AA))),
    Trip("Reykjavík", "Iceland", "20 – 24 Feb", "€1,120", listOf(Color(0xFF3949AB), Color(0xFF00ACC1))),
    Trip("Marrakesh", "Morocco", "8 – 13 Oct", "€690", listOf(Color(0xFFF4511E), Color(0xFFFFB300))),
)

@Composable
private fun TripsScreen(padding: PaddingValues, logs: DiagnosticLogBuffer) {
    val scope = rememberCoroutineScope()
    val booking = remember { mutableStateMapOf<String, Boolean>() }
    val failed = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(Trips, key = Trip::city) { trip ->
            TripCard(
                trip = trip,
                booking = booking[trip.city] == true,
                failed = failed[trip.city] == true,
                onBook = {
                    scope.launch {
                        booking[trip.city] = true
                        failed[trip.city] = false
                        logs.append("INFO", "Booking", "POST /bookings city=${trip.city} token=demo-session-7f3a")
                        delay(700)
                        // The bug the sample asks you to report. The token above is redacted before
                        // it ever reaches the log buffer.
                        logs.append("ERROR", "Booking", "POST /bookings failed: HTTP 502 Bad Gateway")
                        booking[trip.city] = false
                        failed[trip.city] = true
                    }
                },
            )
        }
    }
}

@Composable
private fun TripCard(trip: Trip, booking: Boolean, failed: Boolean, onBook: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.linearGradient(trip.colors)),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(trip.city, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(trip.country, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(trip.dates, style = MaterialTheme.typography.bodyLarge)
                Text(trip.price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Button(onClick = onBook, enabled = !booking) {
                if (booking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Book")
            }
        }
        if (failed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Booking failed. Please try again later.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun InboxScreen(padding: PaddingValues, inbox: List<BugReport>) {
    if (inbox.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Reports you send show up here, exactly as your BugReportSender receives them.\n\n" +
                    "Tap the bug icon to file one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(inbox) { report -> ReportCard(report) }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun ReportCard(report: BugReport) {
    val thumbnails: List<ImageBitmap> = remember(report) {
        report.images.mapNotNull { runCatching { it.bytes.decodeToImageBitmap() }.getOrNull() }
    }
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(report.plainTextDescription.lineSequence().first(), style = MaterialTheme.typography.titleMedium)
            Label("description (Markdown)")
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    report.description,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
            if (thumbnails.isNotEmpty()) {
                Label("images (${report.images.size} JPEG, ${report.images.sumOf { it.bytes.size } / 1024} KB)")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(thumbnails) { bitmap ->
                        // Keep each image's own shape: a cropped one is often wider than it is tall.
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(140.dp)
                                .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
            report.logs?.let { logs ->
                Label("logs (redacted)")
                Text(
                    logs.trimEnd(),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Label("device")
            Text("${report.device}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}
