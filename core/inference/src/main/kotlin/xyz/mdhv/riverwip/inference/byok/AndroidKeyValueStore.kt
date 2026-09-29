package xyz.mdhv.riverwip.inference.byok

import android.content.Context

/** The BYOK settings in the private `byok_config` SharedPreferences the app has always used, so an existing key survives. */
class AndroidKeyValueStore(context: Context) : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences("byok_config", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(entries: Map<String, String>) {
        prefs.edit().apply { entries.forEach { (k, v) -> putString(k, v) } }.apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
