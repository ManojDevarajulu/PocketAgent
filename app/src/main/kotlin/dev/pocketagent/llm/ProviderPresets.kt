package dev.pocketagent.llm

/** A starting point for the setup screen. It has no model name: the user always picks the model. */
data class ProviderPreset(
    val id: String, val label: String, val kind: ProviderKind, val baseUrl: String,
    val imagesByDefault: Boolean, val needsKey: Boolean, val effort: String?,
)

object ProviderPresets {
    private val compat = ProviderKind.OPENAI_COMPAT

    /** The named providers, in display order. Custom is the setup screen's free-entry path, not a preset. */
    val all: List<ProviderPreset> = listOf(
        ProviderPreset("anthropic", "Anthropic", ProviderKind.ANTHROPIC, "https://api.anthropic.com", true, true, "low"),
        ProviderPreset("openrouter", "OpenRouter", compat, "https://openrouter.ai/api/v1", false, true, null),
        ProviderPreset("openai", "OpenAI", compat, "https://api.openai.com/v1", true, true, null),
        ProviderPreset(
            "gemini", "Gemini", compat, "https://generativelanguage.googleapis.com/v1beta/openai/", true, true, null,
        ),
        ProviderPreset("deepseek", "DeepSeek", compat, "https://api.deepseek.com", false, true, null),
        ProviderPreset("groq", "Groq", compat, "https://api.groq.com/openai/v1", false, true, null),
        ProviderPreset("ollama", "Ollama", compat, "http://localhost:11434/v1", false, false, null),
    )

    fun byId(id: String): ProviderPreset? = all.firstOrNull { it.id == id }

    fun configFor(
        preset: ProviderPreset,
        apiKey: String,
        model: String,
        supportsImages: Boolean = preset.imagesByDefault,
    ): ProviderConfig = ProviderConfig(
        kind = preset.kind,
        baseUrl = preset.baseUrl,
        apiKey = apiKey,
        model = model,
        supportsImages = supportsImages,
        effort = preset.effort,
    )
}
