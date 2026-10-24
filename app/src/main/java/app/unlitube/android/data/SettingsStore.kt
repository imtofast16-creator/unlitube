package app.unlitube.android.data

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App settings, persisted in SharedPreferences and exposed as Compose state
 * so toggling a setting re-renders the UI instantly. Everything stays on the
 * phone (PLAN.md Section 7).
 */
object SettingsStore {

    private const val PREFS_NAME = "unlitube_settings"

    private lateinit var appContext: Context
    private var initialized = false

    /** dark, amoled, light, or system */
    var themeMode: String by mutableStateOf("dark")
        private set

    var liteMode: Boolean by mutableStateOf(false)
        private set

    /** Max default playback height in px, 0 means Auto. */
    var qualityCap: Int by mutableStateOf(0)
        private set

    // Distraction Shield (PLAN.md Section 4B.2)
    var hideShorts: Boolean by mutableStateOf(false)
        private set
    var hideComments: Boolean by mutableStateOf(false)
        private set
    var hideRelated: Boolean by mutableStateOf(false)
        private set
    var searchOnlyMode: Boolean by mutableStateOf(false)
        private set

    var sponsorBlockEnabled: Boolean by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true
        val prefs = prefs()
        themeMode = prefs.getString("themeMode", "dark") ?: "dark"
        liteMode = if (prefs.contains("liteMode")) {
            prefs.getBoolean("liteMode", false)
        } else {
            detectLowEnd()
        }
        qualityCap = prefs.getInt("qualityCap", 0)
        hideShorts = prefs.getBoolean("hideShorts", false)
        hideComments = prefs.getBoolean("hideComments", false)
        hideRelated = prefs.getBoolean("hideRelated", false)
        searchOnlyMode = prefs.getBoolean("searchOnlyMode", false)
        sponsorBlockEnabled = prefs.getBoolean("sponsorBlockEnabled", true)
    }

    fun setThemeMode(value: String) {
        themeMode = value
        save { putString("themeMode", value) }
    }

    fun setLiteMode(value: Boolean) {
        liteMode = value
        save { putBoolean("liteMode", value) }
    }

    fun setQualityCap(value: Int) {
        qualityCap = value
        save { putInt("qualityCap", value) }
    }

    fun setHideShorts(value: Boolean) {
        hideShorts = value
        save { putBoolean("hideShorts", value) }
    }

    fun setHideComments(value: Boolean) {
        hideComments = value
        save { putBoolean("hideComments", value) }
    }

    fun setHideRelated(value: Boolean) {
        hideRelated = value
        save { putBoolean("hideRelated", value) }
    }

    fun setSearchOnlyMode(value: Boolean) {
        searchOnlyMode = value
        save { putBoolean("searchOnlyMode", value) }
    }

    fun setSponsorBlockEnabled(value: Boolean) {
        sponsorBlockEnabled = value
        save { putBoolean("sponsorBlockEnabled", value) }
    }

    private fun detectLowEnd(): Boolean {
        val activityManager =
            appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        val totalGb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
        return totalGb <= 3.6
    }

    private fun prefs(): SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun save(block: SharedPreferences.Editor.() -> Unit) {
        if (!initialized) return
        prefs().edit().apply(block).apply()
    }
}
