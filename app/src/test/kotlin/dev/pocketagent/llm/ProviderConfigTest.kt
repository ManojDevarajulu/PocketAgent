package dev.pocketagent.llm

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderConfigTest {
    private fun config(
        baseUrl: String = "https://api.example.com/v1",
        apiKey: String = "sk-test",
        model: String = "some-model",
        contextTokens: Int = 128_000,
        maxOutputTokens: Int = 4096,
        effort: String? = null,
    ) = ProviderConfig(
        ProviderKind.OPENAI_COMPAT, baseUrl, apiKey, model, false, contextTokens, maxOutputTokens, effort,
    )

    private fun errorsOf(c: ProviderConfig) = c.validate().errors

    @Test
    fun httpsUrlIsValid() {
        val check = config().validate()
        assertTrue(check.ok && check.warnings.isEmpty(), "errors=${check.errors} warnings=${check.warnings}")
    }

    @Test
    fun httpToPublicHostIsRejected() {
        assertTrue(errorsOf(config("http://example.com")).any { "https" in it })
    }

    @Test
    fun httpToLocalhostAndPrivateRangesIsAllowedWithWarning() {
        val hosts = listOf(
            "localhost", "foo.local", "10.1.2.3", "172.16.0.1", "172.31.255.1", "192.168.1.5", "100.64.0.1",
        )
        for (h in hosts) {
            val check = config("http://$h:8080/v1").validate()
            assertEquals(Pair(true, 1), Pair(check.ok, check.warnings.size), "host $h: ${check.errors}")
        }
    }

    @Test
    fun httpToJustOutsideThePrivateRangesIsRejected() {
        for (h in listOf("172.32.0.1", "100.128.0.1", "192.169.0.1")) {
            assertFalse(config("http://$h").validate().ok, "host $h")
        }
    }

    @Test
    fun credentialsInUrlAreRejected() {
        assertTrue(errorsOf(config("https://user:pw@host.test")).any { "credentials" in it })
    }

    @Test
    fun userinfoLookAlikeIsRejected() {
        assertTrue(errorsOf(config("https://api.anthropic.com@evil.test")).any { "credentials" in it })
    }

    @Test
    fun hostIsParsedNotPrefixMatched() {
        assertEquals("api.anthropic.com.evil.test", config("https://api.anthropic.com.evil.test").host)
        assertEquals("api.anthropic.com", config("https://API.Anthropic.com/v1").host)
    }

    @Test
    fun ipv6LiteralsOverPlainHttpAreRejected() {
        assertFalse(config("http://[::1]").validate().ok)
    }

    @Test
    fun otherSchemesQueriesAndFragmentsAreRejected() {
        val bad = listOf("ftp://h.test", "file:///etc/passwd", "https://h.test/?k=1", "https://h.test/#x", "https://", "")
        for (u in bad) assertFalse(config(u).validate().ok, "url '$u'")
    }

    @Test
    fun modelNameLimits() {
        assertFalse(config(model = "  ").validate().ok)
        assertFalse(config(model = "a".repeat(101)).validate().ok)
        assertFalse(config(model = "mo\ndel").validate().ok)
        assertTrue(config(model = "a".repeat(100)).validate().ok)
    }

    @Test
    fun keyWithWhitespaceOrNewlineIsRejected() {
        for (k in listOf("sk a", "sk\n", "sk\tb", "sk\u0000")) assertFalse(config(apiKey = k).validate().ok)
    }

    @Test
    fun keyWithInvisibleOrNonAsciiCharactersIsRejected() {
        for (k in listOf("sk-abc​", "﻿sk-abc", "sk-abcé")) {
            assertFalse(config(apiKey = k).validate().ok, "key with char code ${k.first { it !in '!'..'~' }.code}")
        }
    }

    @Test
    fun emptyKeyAllowedOnlyForPlainHttpLocal() {
        assertTrue(config("http://localhost:11434/v1", apiKey = "").validate().ok)
        assertFalse(config("https://api.example.com", apiKey = "").validate().ok)
        assertFalse(config("http://example.com", apiKey = "").validate().ok)
    }

    @Test
    fun numericLimitsAreChecked() {
        assertFalse(config(contextTokens = 3999).validate().ok)
        assertFalse(config(contextTokens = 2_000_001).validate().ok)
        assertFalse(config(maxOutputTokens = 511).validate().ok)
        assertFalse(config(maxOutputTokens = 8193).validate().ok)
        assertFalse(config(effort = "ultra").validate().ok)
        assertTrue(config(contextTokens = 4000, maxOutputTokens = 512, effort = "xhigh").validate().ok)
        assertTrue(config(contextTokens = 2_000_000, maxOutputTokens = 8192, effort = "low").validate().ok)
    }

    @Test
    fun toStringNeverContainsTheKey() {
        val key = "sk-secret-123"
        val bad = config(baseUrl = "http://example.com", apiKey = "$key bad", model = "")
        assertFalse(bad.toString().contains(key))
        assertTrue(bad.toString().contains("apiKey=***"))
        assertFalse(bad.validate().errors.any { key in it })
    }
}
