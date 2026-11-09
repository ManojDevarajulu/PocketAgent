package dev.pocketagent.ui

import android.app.Activity
import android.content.Context
import dev.pocketagent.ui.kit.ThemeMode

/** Base class for the app's screens: applies the Appearance setting (system, light or dark) before anything is drawn. */
abstract class ThemedActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeMode.wrap(newBase))
    }
}
