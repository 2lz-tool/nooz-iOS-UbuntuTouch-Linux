package xyz.mdhv.riverwip.desktop

import okio.Path
import okio.Path.Companion.toPath

/**
 * Where Nooz keeps things on a Linux desktop, per the XDG base directory spec:
 * data in `$XDG_DATA_HOME/nooz`, cache in `$XDG_CACHE_HOME/nooz`, settings in `$XDG_CONFIG_HOME/nooz`.
 * `NOOZ_HOME`, if set, puts all three under one directory (portable installs, tests).
 */
class XdgDirs(
    val data: Path,
    val cache: Path,
    val config: Path,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv(), home: String = System.getProperty("user.home")): XdgDirs {
            env["NOOZ_HOME"]?.takeIf { it.isNotBlank() }?.let { root ->
                val base = root.toPath()
                return XdgDirs(base / "data", base / "cache", base / "config")
            }
            fun dir(variable: String, fallback: String): Path =
                (env[variable]?.takeIf { it.isNotBlank() && it.startsWith("/") } ?: "$home/$fallback").toPath() / "nooz"
            return XdgDirs(
                data = dir("XDG_DATA_HOME", ".local/share"),
                cache = dir("XDG_CACHE_HOME", ".cache"),
                config = dir("XDG_CONFIG_HOME", ".config"),
            )
        }
    }
}
