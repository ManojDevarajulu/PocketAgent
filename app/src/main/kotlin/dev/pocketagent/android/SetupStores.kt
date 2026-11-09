package dev.pocketagent.android

import android.content.Context
import dev.pocketagent.setup.SetupSettings
import dev.pocketagent.setup.StoredConfigRepository

/** One process-wide repository and settings object over the private setup preferences. */
object SetupStores {
    private class Holder(val repository: StoredConfigRepository, val settings: SetupSettings)

    @Volatile
    private var holder: Holder? = null

    private fun holder(context: Context): Holder = holder ?: synchronized(this) {
        holder ?: run {
            val prefs = (context.applicationContext ?: context).getSharedPreferences("pocketagent_setup", Context.MODE_PRIVATE)
            val store = PrefsKeyValueStore(prefs)
            Holder(StoredConfigRepository(store, KeystoreSecretBox()), SetupSettings(store)).also { holder = it }
        }
    }

    fun repository(context: Context): StoredConfigRepository = holder(context).repository

    fun settings(context: Context): SetupSettings = holder(context).settings
}
