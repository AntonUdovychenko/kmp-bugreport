package io.github.antonudovychenko.bugreport

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

internal actual fun currentDeviceInfo(): DeviceInfo {
    val device = UIDevice.currentDevice
    return DeviceInfo(
        platform = BugReportPlatform.Ios,
        operatingSystem = device.systemName,
        osVersion = device.systemVersion,
        // `model` is just "iPhone"; the machine identifier ("iPhone16,2") says which one.
        model = listOfNotNull(device.model, machineIdentifier()).distinct().joinToString(" "),
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun machineIdentifier(): String? = memScoped {
    val info = alloc<utsname>()
    if (uname(info.ptr) != 0) return@memScoped null
    info.machine.toKString().takeIf(String::isNotBlank)
}
