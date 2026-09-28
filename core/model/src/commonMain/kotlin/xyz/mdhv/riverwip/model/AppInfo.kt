package xyz.mdhv.riverwip.model

/**
 * The single runtime reference for the app's public-facing package.
 *
 * The final package — `dev.asystemofcells.nooz`, as registered in Play Console
 * (decided 2026-07; see STATE.md §RESERVED) — lives in exactly two places:
 * `riverwip.applicationId` in `gradle.properties` (drives the app module's
 * `applicationId` only) and this constant (drives runtime references such as
 * ContentProvider authorities and DataStore file names). Deliberately distinct
 * from `riverwip.packageBase`, which every module's own `namespace` derives
 * from and which stays `xyz.mdhv.riverwip` — that one has to keep matching the
 * actual Kotlin package declarations across the source tree.
 */
object AppInfo {
    const val PACKAGE_BASE: String = "dev.asystemofcells.nooz"

    /** Working product name. RESERVED — never surface a final name in product copy. */
    const val WORKING_NAME: String = "river"
}
