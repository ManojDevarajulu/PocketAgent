package dev.pocketagent.safety

import java.util.Locale

/**
 * Apps the agent never opens or acts in: banking, payments, password managers, and the system screens that
 * could switch off its own safety. Matches by package, by app name, and by word-start patterns on the
 * name or on each dot-separated part of the package. Over-blocking is accepted.
 */
class BlockedApps(
    private val packages: Set<String> = DEFAULT_PACKAGES,
    private val patterns: List<String> = DEFAULT_PATTERNS,
    private val names: Set<String> = DEFAULT_NAMES,
) {
    private val labelPattern = if (patterns.isEmpty()) null else wordStartPattern(patterns)

    fun isBlocked(packageName: String, label: String?): Boolean {
        if (packageName in packages) return true
        val name = matchForm(label.orEmpty()).trim()
        if (name.isNotEmpty()) {
            if (name.lowercase(Locale.ROOT) in names) return true
            if (labelPattern?.find(name) == true) return true
        }
        return packageName.split('.').any { part -> part.isNotEmpty() && patterns.any { part.startsWith(it, ignoreCase = true) } }
    }

    companion object {
        val DEFAULT_PACKAGES: Set<String> = setOf(
            "com.google.android.apps.nbu.paisa.user", "net.one97.paytm", "com.phonepe.app", "in.org.npci.upiapp",
            "com.google.android.apps.walletnfcrel", "com.x8bit.bitwarden", "com.agilebits.onepassword",
            "com.lastpass.lpandroid", "com.google.android.apps.authenticator2",
            "com.android.settings", "com.android.systemui", "com.google.android.packageinstaller",
            "com.android.packageinstaller", "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        )
        val DEFAULT_PATTERNS: List<String> = listOf("bank", "pay", "wallet", "upi", "password")

        /** Lower-case app names that the word patterns miss, used when only the name is known (open_app). */
        val DEFAULT_NAMES: Set<String> = setOf(
            "phonepe", "paytm", "google pay", "gpay", "bhim", "bitwarden", "1password", "lastpass", "authenticator",
            "google authenticator", "settings",
        )
    }
}
