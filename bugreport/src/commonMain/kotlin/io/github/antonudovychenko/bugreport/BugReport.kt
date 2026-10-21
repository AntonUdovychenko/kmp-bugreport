package io.github.antonudovychenko.bugreport

/**
 * Everything the user submitted, ready to hand to a [BugReportSender].
 *
 * @property description the user's description as Markdown (bold, italic, strikethrough, inline
 *   code, links and lists are produced by the editor's toolbar).
 * @property plainTextDescription the same description with formatting removed — handy for an issue
 *   title or a log line.
 * @property images screenshots and picked images, each with any markup and crop already baked in.
 * @property logs the diagnostic log snapshot taken when the report was opened, or `null` when no log
 *   source was given or the user unticked "Attach diagnostic logs".
 * @property device the platform the report came from.
 * @property metadata whatever the host app supplied (build number, environment, user id, ...).
 */
public class BugReport(
    public val description: String,
    public val plainTextDescription: String,
    public val images: List<BugReportImage>,
    public val logs: String?,
    public val device: DeviceInfo,
    public val metadata: Map<String, String>,
) {
    override fun toString(): String =
        "BugReport(description=${description.length} chars, images=${images.size}, " +
            "logs=${logs?.length ?: 0} chars, device=$device, metadata=$metadata)"
}

/** One encoded image attached to a [BugReport]. Always a JPEG today; [mimeType] says so explicitly. */
public class BugReportImage(
    public val bytes: ByteArray,
    public val fileName: String,
    public val mimeType: String = "image/jpeg",
) {
    override fun toString(): String = "BugReportImage($fileName, $mimeType, ${bytes.size} bytes)"
}

/** The platform family a report was filed from. */
public enum class BugReportPlatform { Android, Ios, Desktop, Web }

/**
 * A best-effort description of the reporting device, collected without any permission.
 *
 * @property operatingSystem e.g. `Android`, `iOS`, `Mac OS X`, or the browser's user agent on the web.
 * @property osVersion e.g. `15 (API 35)`, `18.2`, `15.1`.
 * @property model the hardware model where the platform exposes one.
 */
public class DeviceInfo(
    public val platform: BugReportPlatform,
    public val operatingSystem: String,
    public val osVersion: String,
    public val model: String? = null,
) {
    override fun toString(): String = buildString {
        append(operatingSystem)
        if (osVersion.isNotBlank()) append(' ').append(osVersion)
        model?.takeIf(String::isNotBlank)?.let { append(" (").append(it).append(')') }
    }

    public companion object {
        /** The device this code is running on. */
        public val current: DeviceInfo by lazy { currentDeviceInfo() }
    }
}

internal expect fun currentDeviceInfo(): DeviceInfo

/**
 * The model of an Apple device: UIKit's model ("iPhone") and the machine identifier
 * ("iPhone16,2") that says which one. In the simulator `uname` reports the Mac's architecture
 * ("arm64"), so the simulated device's identifier ([simulatorMachine]) is used instead.
 */
internal fun appleDeviceModel(model: String, machine: String?, simulatorMachine: String?): String =
    if (!simulatorMachine.isNullOrBlank()) {
        "$model Simulator $simulatorMachine"
    } else {
        listOfNotNull(model, machine?.takeIf(String::isNotBlank)).distinct().joinToString(" ")
    }
