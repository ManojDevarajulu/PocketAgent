package dev.pocketagent.llm

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Opt-in check that sends one small real request. It is skipped unless all four POCKET_AGENT_LIVE_* variables
 * are set (see docs/TESTING.md). Messages here never include the key.
 */
class LiveProviderCheckTest {
    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    @Test
    fun liveProviderAnswersWithAToolCall() {
        val key = env("POCKET_AGENT_LIVE_KEY")
        val kindName = env("POCKET_AGENT_LIVE_KIND")
        val baseUrl = env("POCKET_AGENT_LIVE_BASE_URL")
        val model = env("POCKET_AGENT_LIVE_MODEL")
        assumeTrue("POCKET_AGENT_LIVE_* variables not all set; skipping live check", key != null && kindName != null && baseUrl != null && model != null)
        val kind = ProviderKind.entries.firstOrNull { it.name == kindName }
        assumeTrue("POCKET_AGENT_LIVE_KIND must be ANTHROPIC or OPENAI_COMPAT", kind != null)

        val config = ProviderConfig(
            kind = kind!!,
            baseUrl = baseUrl!!,
            apiKey = key!!,
            model = model!!,
            supportsImages = false,
            effort = if (kind == ProviderKind.ANTHROPIC) "low" else null,
        )
        val client = Providers.create(config, HttpCaller(OkHttpTransport()))
        val result = runBlocking { ProviderCheck.run(client) }
        assertEquals(CheckResult.Works, result, "live check for kind=$kind host=${config.host} model=$model returned $result")
    }
}
