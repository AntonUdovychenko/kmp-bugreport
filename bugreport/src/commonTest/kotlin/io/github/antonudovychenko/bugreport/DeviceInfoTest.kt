package io.github.antonudovychenko.bugreport

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceInfoTest {
    @Test
    fun appleDeviceIsNamedByItsMachineIdentifier() {
        assertEquals("iPhone iPhone16,2", appleDeviceModel("iPhone", machine = "iPhone16,2", simulatorMachine = null))
        assertEquals("iPhone", appleDeviceModel("iPhone", machine = null, simulatorMachine = null))
    }

    @Test
    fun simulatorIsNamedByTheSimulatedDeviceNotTheMacArchitecture() {
        // Reported as "iPhone arm64" before: uname gives the host's architecture in the simulator.
        assertEquals(
            "iPhone Simulator iPhone18,1",
            appleDeviceModel("iPhone", machine = "arm64", simulatorMachine = "iPhone18,1"),
        )
        assertEquals("iPhone iPhone16,2", appleDeviceModel("iPhone", machine = "iPhone16,2", simulatorMachine = ""))
    }

    @Test
    fun deviceInfoPutsTheModelInParentheses() {
        val info = DeviceInfo(BugReportPlatform.Ios, "iOS", "27.0", appleDeviceModel("iPhone", "arm64", "iPhone18,1"))
        assertEquals("iOS 27.0 (iPhone Simulator iPhone18,1)", info.toString())
    }
}
