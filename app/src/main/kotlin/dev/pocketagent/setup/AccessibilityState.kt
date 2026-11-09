package dev.pocketagent.setup

object AccessibilityState {
    /** [enabledServices] is Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, a `:`-separated list of component names. */
    fun isEnabled(enabledServices: String?, packageName: String, serviceClass: String): Boolean {
        if (enabledServices.isNullOrBlank()) return false
        val pkg = packageName.lowercase()
        val full = serviceClass.lowercase()
        val relative = if (full.startsWith("$pkg.")) full.removePrefix(pkg) else full
        val accepted = setOf("$pkg/$full", "$pkg/$relative")
        return enabledServices.split(':').any { it.trim().lowercase() in accepted }
    }
}
