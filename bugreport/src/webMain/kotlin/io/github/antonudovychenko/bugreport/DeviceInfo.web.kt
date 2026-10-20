package io.github.antonudovychenko.bugreport

import kotlinx.browser.window

internal actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    platform = BugReportPlatform.Web,
    operatingSystem = "Web",
    osVersion = "",
    model = window.navigator.userAgent.ifBlank { null },
)
