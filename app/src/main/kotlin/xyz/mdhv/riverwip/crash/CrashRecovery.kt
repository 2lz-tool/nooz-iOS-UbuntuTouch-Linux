package xyz.mdhv.riverwip.crash

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File

/**
 * Device-only crash capture, ported from the Hyle Design System's
 * `crash-recovery` module (owner's #17). Faithful to that module's philosophy:
 * an uncaught-exception handler writes a single report to the app's private
 * files dir and **nothing is ever transmitted** — no analytics, no network, no
 * third-party SDK. The report waits there until the user reads it (Settings ›
 * "Last crash"), copies/exports it, or clears it.
 *
 * Every operation is `runCatching`-guarded so the handler can never itself
 * crash. CI never sees these — CI runs unit tests, never launches the app.
 */
object CrashRecovery {
    private const val FILE_NAME = "crash_report.txt"

    /** Install the handler once, from [Application.onCreate]. Chains to any prior handler. */
    fun install(app: Application, appLabel: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { capture(app, appLabel, throwable, thread.name) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** For a synchronous init failure caught in your own `catch` block. */
    fun captureInitError(context: Context, appLabel: String, throwable: Throwable) {
        runCatching { capture(context, appLabel, throwable, Thread.currentThread().name) }
    }

    private fun capture(context: Context, appLabel: String, throwable: Throwable, threadName: String) {
        val report = CrashReport.of(appLabel, System.currentTimeMillis(), threadName, throwable, deviceInfo(context))
        file(context).writeText(report.encode())
        android.util.Log.e("CrashRecovery", "captured crash for $appLabel", throwable)
    }

    @Suppress("DEPRECATION")
    private fun legacyVersionCode(info: android.content.pm.PackageInfo): Long = info.versionCode.toLong()

    private fun deviceInfo(context: Context): CrashReport.DeviceInfo = runCatching {
        val pm = context.applicationContext.packageManager
        val info = pm.getPackageInfo(context.applicationContext.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else legacyVersionCode(info)
        CrashReport.DeviceInfo(
            appVersionName = info.versionName,
            appVersionCode = versionCode,
            os = "Android SDK ${Build.VERSION.SDK_INT}",
            deviceManufacturer = Build.MANUFACTURER ?: "?",
            deviceModel = Build.MODEL ?: "?",
        )
    }.getOrDefault(CrashReport.DeviceInfo(null, null, "Android SDK ${Build.VERSION.SDK_INT}", "?", "?"))

    /** Non-null if a crash was captured and not yet cleared. */
    fun pending(context: Context): CrashReport.Decoded? = runCatching {
        file(context).takeIf { it.exists() }?.readText()?.let(CrashReport::decode)
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)
}
