package dev.pocketagent.ui.kit

import android.content.Context
import android.content.res.Configuration
import dev.pocketagent.android.SetupStores

/**
 * The app's appearance: follow the phone, or force light or dark. Screens ask [Kit.color] for colours, so the choice
 * reaches the floating overlays as well as the activities, which wrap their base context with [wrap].
 */
object ThemeMode {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"
    val all = listOf(SYSTEM, LIGHT, DARK)

    @Volatile
    private var mode: String? = null
    private val cache = HashMap<Boolean, Context>()

    fun current(c: Context): String {
        mode?.let { return it }
        val m = SetupStores.settings(c).theme
        mode = m
        return m
    }

    fun set(c: Context, value: String) {
        SetupStores.settings(c).theme = value
        mode = value
        synchronized(cache) { cache.clear() }
    }

    /** True or false when the user forced a theme, null to follow the phone. */
    fun forcedNight(c: Context): Boolean? = when (current(c)) {
        LIGHT -> false
        DARK -> true
        else -> null
    }

    fun isNight(c: Context): Boolean = forcedNight(c)
        ?: (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)

    private fun forced(base: Context, night: Boolean): Context {
        val cfg = Configuration(base.resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return base.createConfigurationContext(cfg)
    }

    /** For an activity's attachBaseContext: the same context, with the chosen theme applied. */
    fun wrap(base: Context): Context {
        val night = forcedNight(base) ?: return base
        return forced(base, night)
    }

    /** A context that resolves colours in the chosen theme, for views that live outside an activity (overlays). */
    fun colorContext(c: Context, night: Boolean): Context = synchronized(cache) {
        cache.getOrPut(night) { forced(c.applicationContext ?: c, night) }
    }
}
