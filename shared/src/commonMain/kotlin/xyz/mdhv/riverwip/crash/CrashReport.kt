package xyz.mdhv.riverwip.crash

import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A captured crash (ported from Hyle's `CrashReport`). [headline] is the one
 * line worth reading first; [device] is reproduction metadata; [trace] is the
 * full stack, kept separate so a UI can hide it behind a details toggle.
 */
data class CrashReport(
    val appLabel: String,
    val whenMillis: Long,
    val threadName: String,
    val headline: String,
    val device: DeviceInfo,
    val trace: String,
) {
    data class DeviceInfo(
        val appVersionName: String?,
        val appVersionCode: Long?,
        val os: String,
        val deviceManufacturer: String,
        val deviceModel: String,
    )

    fun render(): String = buildString {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        append(appLabel).append(" crash\n")
        append("when: ").append(format.format(Date(whenMillis))).append('\n')
        append("thread: ").append(threadName).append('\n')
        append("app version: ").append(device.appVersionName ?: "?")
        append(" (").append(device.appVersionCode?.toString() ?: "?").append(")\n")
        append("device: ").append(device.deviceManufacturer).append(' ').append(device.deviceModel)
        append(" · ").append(device.os).append("\n\n")
        append(headline).append("\n\n")
        append(trace)
    }

    fun encode(): String = "$headline\n\n${render()}"

    companion object {
        fun headlineOf(throwable: Throwable): String {
            val type = throwable.javaClass.simpleName.ifBlank { throwable.javaClass.name }
            val message = throwable.message?.takeIf { it.isNotBlank() }
            return if (message != null) "$type: $message" else type
        }

        fun stackTraceOf(throwable: Throwable): String =
            StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()

        fun of(
            appLabel: String,
            whenMillis: Long,
            threadName: String,
            throwable: Throwable,
            device: DeviceInfo,
        ): CrashReport = CrashReport(
            appLabel = appLabel,
            whenMillis = whenMillis,
            threadName = threadName,
            headline = headlineOf(throwable),
            device = device,
            trace = stackTraceOf(throwable),
        )

        fun decode(persisted: String): Decoded {
            val separator = "\n\n"
            val splitAt = persisted.indexOf(separator)
            return if (splitAt >= 0) {
                Decoded(persisted.substring(0, splitAt), persisted.substring(splitAt + separator.length))
            } else {
                Decoded(persisted.lines().firstOrNull().orEmpty(), persisted)
            }
        }
    }

    data class Decoded(val headline: String, val fullReport: String)
}
