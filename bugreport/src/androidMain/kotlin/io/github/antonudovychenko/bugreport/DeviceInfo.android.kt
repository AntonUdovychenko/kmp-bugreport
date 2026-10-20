package io.github.antonudovychenko.bugreport

import android.os.Build

internal actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    platform = BugReportPlatform.Android,
    operatingSystem = "Android",
    osVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    model = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { !it.isNullOrBlank() }
        .joinToString(" ")
        .ifBlank { null },
)
