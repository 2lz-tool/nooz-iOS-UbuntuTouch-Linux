package xyz.mdhv.riverwip

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    // The reader's chosen interface language, applied before any resource is
    // resolved. A no-op on API 33+, where the platform has already done it.
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as RiverApplication).container
        (container.locale as? AndroidLocaleController)?.activity = this
        container.windowControls = AndroidWindowControls(this)
        setContent { NoozApp(container) }
    }

    override fun onDestroy() {
        val container = (application as RiverApplication).container
        (container.locale as? AndroidLocaleController)?.takeIf { it.activity === this }?.activity = null
        super.onDestroy()
    }
}

/** In-app window brightness (per-window, no permission, resets with the app) and system-bar icon contrast. */
private class AndroidWindowControls(private val activity: Activity) : WindowControls {
    override fun adjustBrightness(delta: Float) {
        val w = activity.window
        val attrs = w.attributes
        val current = if (attrs.screenBrightness < 0f) 0.5f else attrs.screenBrightness
        attrs.screenBrightness = (current + delta).coerceIn(0.05f, 1f)
        w.attributes = attrs
    }

    override fun setDarkSurface(dark: Boolean) {
        val window = activity.window
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
