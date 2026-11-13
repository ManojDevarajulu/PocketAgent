package dev.pocketagent.device

import dev.pocketagent.safety.BlockedApps
import java.util.Locale

data class LauncherApp(val label: String, val packageName: String)

sealed interface AppResolution {
    data class Launch(val app: LauncherApp) : AppResolution
    data class Refused(val reason: String) : AppResolution
}

/**
 * Turns the name the model gave to open_app into one launcher app. Case-insensitive on trimmed text. Three
 * tiers, and the first one that matches anything decides: the label equals the name; the label contains the
 * name ("maps" finds "Google Maps"); the name contains the label as whole words ("Google Maps" finds an app
 * labelled "Maps", and labels under three characters never count). More than one match in the deciding tier is
 * refused so the model can ask the user.
 */
class AppMatcher(private val blocked: BlockedApps) {
    fun resolve(name: String, apps: List<LauncherApp>): AppResolution {
        val wanted = name.trim().lowercase(Locale.ROOT)
        if (wanted.isEmpty()) return AppResolution.Refused("no app name given")

        val candidates = apps.filter { it.label.trim().lowercase(Locale.ROOT) == wanted }
            .ifEmpty { apps.filter { it.label.lowercase(Locale.ROOT).contains(wanted) } }
            .ifEmpty { apps.filter { nameContainsLabel(wanted, it.label) } }

        return when (candidates.size) {
            0 -> AppResolution.Refused("no installed app named ${name.trim()}")
            1 -> {
                val app = candidates.single()
                if (blocked.isBlocked(app.packageName, app.label)) {
                    AppResolution.Refused("${app.label} is on the block list")
                } else {
                    AppResolution.Launch(app)
                }
            }
            else -> {
                val labels = candidates.map { it.label }.sorted().take(MAX_LISTED).joinToString(", ")
                AppResolution.Refused("several apps match: $labels; ask the user which one")
            }
        }
    }

    private fun nameContainsLabel(wanted: String, label: String): Boolean {
        val short = label.trim().lowercase(Locale.ROOT)
        if (short.length < MIN_LABEL_LENGTH) return false
        return Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(short) + "(?![\\p{L}\\p{N}])").containsMatchIn(wanted)
    }

    private companion object {
        const val MAX_LISTED = 5
        const val MIN_LABEL_LENGTH = 3
    }
}
