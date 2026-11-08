package dev.pocketagent.llm

object Providers {
    /** Builds the client for [config].kind. The message lists validation errors, which never contain the key. */
    fun create(config: ProviderConfig, caller: HttpCaller): LlmClient {
        val check = config.validate()
        require(check.ok) { "Invalid provider config: " + check.errors.joinToString(" ") }
        return when (config.kind) {
            ProviderKind.ANTHROPIC -> AnthropicClient(config, caller)
            ProviderKind.OPENAI_COMPAT -> OpenAiCompatClient(config, caller)
        }
    }
}
