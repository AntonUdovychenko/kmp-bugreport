package io.github.antonudovychenko.bugreport

internal actual fun currentDeviceInfo(): DeviceInfo = DeviceInfo(
    platform = BugReportPlatform.Desktop,
    operatingSystem = System.getProperty("os.name").orEmpty().ifBlank { "Desktop" },
    osVersion = System.getProperty("os.version").orEmpty(),
    model = listOfNotNull(
        System.getProperty("os.arch"),
        System.getProperty("java.version")?.let { "Java $it" },
    ).joinToString(", ").ifBlank { null },
)
