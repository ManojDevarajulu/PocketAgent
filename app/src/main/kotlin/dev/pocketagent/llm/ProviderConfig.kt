package dev.pocketagent.llm

import java.net.URI
import java.net.URISyntaxException

enum class ProviderKind { ANTHROPIC, OPENAI_COMPAT }

/**
 * One configured LLM endpoint. The key is bring-your-own and is never printed: [toString] masks it and no
 * validation message includes it.
 */
data class ProviderConfig(
    val kind: ProviderKind,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val supportsImages: Boolean,
    val contextTokens: Int = 128_000,
    val maxOutputTokens: Int = 4096,
    val effort: String? = null,
) {
    private val parsed: URI? = try {
        URI(baseUrl)
    } catch (_: URISyntaxException) {
        null
    }

    /**
     * The host as parsed by [java.net.URI] (never by string prefix), lower-cased, for exact comparisons.
     * It is the empty string when [baseUrl] cannot be parsed or has no host, so it never equals a real host.
     * IPv6 literals keep their brackets (for example `[::1]`).
     */
    val host: String get() = parsed?.host?.lowercase().orEmpty()

    fun validate(): ConfigCheck {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val plainHttpLocal = checkUrl(errors, warnings)
        checkKey(errors, plainHttpLocal)
        if (model.isBlank() || model.length > MAX_MODEL_LENGTH || model.any { it.isISOControl() }) {
            errors += "Model name must be 1 to $MAX_MODEL_LENGTH characters with no control characters."
        }
        if (contextTokens !in CONTEXT_RANGE) {
            errors += "Context size must be between ${CONTEXT_RANGE.first} and ${CONTEXT_RANGE.last} tokens."
        }
        if (maxOutputTokens !in OUTPUT_RANGE) {
            errors += "Max output must be between ${OUTPUT_RANGE.first} and ${OUTPUT_RANGE.last} tokens."
        }
        if (effort != null && effort !in EFFORTS) {
            errors += "Effort must be one of ${EFFORTS.joinToString(", ")}."
        }
        return ConfigCheck(errors, warnings)
    }

    /** Validates the base URL; returns true when it is a plain-http URL to a local server. */
    private fun checkUrl(errors: MutableList<String>, warnings: MutableList<String>): Boolean {
        val uri = parsed
        if (uri == null || host.isEmpty()) {
            errors += "Base URL is not a valid URL with a host."
            return false
        }
        if (uri.rawUserInfo != null) errors += "Base URL must not contain credentials (user:password@)."
        if (uri.rawQuery != null || uri.rawFragment != null) {
            errors += "Base URL must not contain a query string or fragment."
        }
        return when (uri.scheme?.lowercase()) {
            "https" -> false
            "http" -> if (isLocalHost(host)) {
                warnings += "This connection is not encrypted. Use it only on a network you trust."
                true
            } else {
                errors += "Plain http is only allowed for localhost, .local names and private network addresses; use https."
                false
            }
            else -> {
                errors += "Base URL must use https."
                false
            }
        }
    }

    private fun checkKey(errors: MutableList<String>, plainHttpLocal: Boolean) {
        if (apiKey.isEmpty()) {
            if (!plainHttpLocal) errors += "An API key is required unless the server is a local plain-http server."
        } else if (apiKey.any { it.isWhitespace() || it.isISOControl() }) {
            errors += "API key must not contain spaces, line breaks or control characters."
        }
    }

    override fun toString() =
        "ProviderConfig(kind=$kind, baseUrl=$baseUrl, apiKey=***, model=$model, supportsImages=$supportsImages, " +
            "contextTokens=$contextTokens, maxOutputTokens=$maxOutputTokens, effort=$effort)"

    private companion object {
        const val MAX_MODEL_LENGTH = 100
        val CONTEXT_RANGE = 4_000..2_000_000
        val OUTPUT_RANGE = 512..8_192
        val EFFORTS = listOf("low", "medium", "high", "xhigh", "max")
        val IPV4 = Regex("""(0|[1-9]\d{0,2})(\.(0|[1-9]\d{0,2})){3}""")

        fun isLocalHost(host: String): Boolean = host == "localhost" || host.endsWith(".local") || isPrivateIpv4(host)

        /** Only IPv4 literals are classified; IPv6 and every other hostname count as public. */
        fun isPrivateIpv4(host: String): Boolean {
            if (!IPV4.matches(host)) return false
            val o = host.split('.').map { it.toInt() }
            if (o.any { it > 255 }) return false
            return o[0] == 10 ||
                (o[0] == 172 && o[1] in 16..31) ||
                (o[0] == 192 && o[1] == 168) ||
                (o[0] == 100 && o[1] in 64..127)
        }
    }
}

data class ConfigCheck(val errors: List<String>, val warnings: List<String>) {
    val ok: Boolean get() = errors.isEmpty()
}
