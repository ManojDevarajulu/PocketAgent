package dev.pocketagent.llm

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderPresetsTest {
    @Test
    fun everyPresetPassesValidationWithAKeyAndModel() {
        for (p in ProviderPresets.all) {
            val key = if (p.needsKey) "sk-test-key" else ""
            val check = ProviderPresets.configFor(p, key, "some-model").validate()
            assertTrue(check.ok, "${p.id}: ${check.errors}")
        }
    }

    @Test
    fun idsAreUnique() {
        val ids = ProviderPresets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            listOf("anthropic", "openrouter", "openai", "gemini", "deepseek", "groq", "ollama"),
            ids,
        )
        assertNotNull(ProviderPresets.byId("groq"))
        assertNull(ProviderPresets.byId("nope"))
    }

    @Test
    fun onlyAnthropicSendsEffortLow() {
        for (p in ProviderPresets.all) {
            assertEquals(if (p.id == "anthropic") "low" else null, p.effort, p.id)
        }
        assertEquals("low", ProviderPresets.configFor(ProviderPresets.byId("anthropic")!!, "k", "m").effort)
    }

    @Test
    fun imagesByDefaultMatchTheSpec() {
        val on = setOf("anthropic", "openai", "gemini")
        for (p in ProviderPresets.all) assertEquals(p.id in on, p.imagesByDefault, p.id)
        val openRouter = ProviderPresets.byId("openrouter")!!
        assertEquals(false, ProviderPresets.configFor(openRouter, "k", "m").supportsImages)
        assertEquals(true, ProviderPresets.configFor(openRouter, "k", "m", supportsImages = true).supportsImages)
    }

    @Test
    fun anthropicPresetIsAnthropicKind() {
        val a = ProviderPresets.byId("anthropic")!!
        assertEquals(ProviderKind.ANTHROPIC, a.kind)
        assertEquals("https://api.anthropic.com", a.baseUrl)
        assertTrue(ProviderPresets.all.filter { it.id != "anthropic" }.all { it.kind == ProviderKind.OPENAI_COMPAT })
    }

    @Test
    fun ollamaIsPlainHttpLocalAndNeedsNoKey() {
        val o = ProviderPresets.byId("ollama")!!
        assertEquals("http://localhost:11434/v1", o.baseUrl)
        assertEquals(false, o.needsKey)
        assertTrue(ProviderPresets.all.filter { it.id != "ollama" }.all { it.needsKey })
    }

    @Test
    fun noPresetCarriesAModelName() {
        val fields = ProviderPreset::class.java.declaredFields.map { it.name }
        assertTrue("model" !in fields, fields.toString())
    }
}
