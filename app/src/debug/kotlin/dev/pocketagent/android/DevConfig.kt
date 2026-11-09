package dev.pocketagent.android

import dev.pocketagent.BuildConfig
import dev.pocketagent.llm.ProviderConfig
import dev.pocketagent.llm.ProviderKind

/** Debug builds only: a provider config taken from local.properties at build time. */
object DevConfig {
    fun load(): ProviderConfig? = fromValues(
        BuildConfig.POCKETAGENT_KIND,
        BuildConfig.POCKETAGENT_BASE_URL,
        BuildConfig.POCKETAGENT_API_KEY,
        BuildConfig.POCKETAGENT_MODEL,
        BuildConfig.POCKETAGENT_SUPPORTS_IMAGES,
    )

    /** Returns null for an unknown kind, a blank field, or a config that fails validation. */
    internal fun fromValues(kind: String, baseUrl: String, apiKey: String, model: String, supportsImages: String): ProviderConfig? {
        val parsedKind = ProviderKind.entries.firstOrNull { it.name == kind.trim() } ?: return null
        if (baseUrl.isBlank() || apiKey.isBlank() || model.isBlank() || supportsImages.isBlank()) return null
        val config = ProviderConfig(
            kind = parsedKind,
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
            supportsImages = supportsImages.trim().toBoolean(),
        )
        return if (config.validate().ok) config else null
    }
}
