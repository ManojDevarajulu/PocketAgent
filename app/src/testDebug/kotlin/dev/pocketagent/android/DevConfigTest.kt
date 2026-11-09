package dev.pocketagent.android

import dev.pocketagent.llm.ProviderKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevConfigTest {
    private fun build(
        kind: String = "ANTHROPIC",
        baseUrl: String = "https://api.example.com",
        key: String = "test-key-123",
        model: String = "some-model",
        images: String = "true",
    ) = DevConfig.fromValues(kind, baseUrl, key, model, images)

    @Test
    fun validValuesBuildAConfig() {
        val config = assertNotNull(build())
        assertEquals(ProviderKind.ANTHROPIC, config.kind)
        assertEquals("https://api.example.com", config.baseUrl)
        assertEquals("some-model", config.model)
        assertTrue(config.supportsImages)
    }

    @Test
    fun unknownKindGivesNull() {
        assertNull(build(kind = "NOT_A_KIND"))
    }

    @Test
    fun blankModelGivesNull() {
        assertNull(build(model = "  "))
    }

    @Test
    fun plainHttpToTheInternetGivesNull() {
        assertNull(build(baseUrl = "http://api.example.com"))
    }

    @Test
    fun supportsImagesParsesTrueAndFalse() {
        assertTrue(assertNotNull(build(images = "true")).supportsImages)
        assertFalse(assertNotNull(build(images = "false")).supportsImages)
    }
}
