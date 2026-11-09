package dev.pocketagent.setup

import org.junit.Test
import kotlin.test.assertTrue

class SpeechErrorsTest {
    private fun msg(code: Int) = SpeechErrors.messageFor(code).lowercase()

    @Test
    fun everyKnownCodeHasAMessage() {
        (1..13).forEach { assertTrue(SpeechErrors.messageFor(it).isNotBlank(), "code $it") }
    }

    @Test
    fun unknownCodesFallBack() {
        listOf(0, 99, -1).forEach { assertTrue(msg(it).contains("type"), "code $it") }
    }

    @Test
    fun nothingHeardMentionsHearing() {
        listOf(6, 7).forEach { assertTrue(msg(it).contains("hear"), "code $it") }
    }

    @Test
    fun connectionErrorsMentionAConnection() {
        listOf(1, 2, 4, 11).forEach { assertTrue(msg(it).contains("connection"), "code $it") }
    }

    @Test
    fun busyMentionsBusy() = assertTrue(msg(8).contains("busy"))

    @Test
    fun permissionMentionsTheMicrophone() = assertTrue(msg(9).contains("microphone"))

    @Test
    fun languageErrorsMentionLanguage() {
        listOf(12, 13).forEach { assertTrue(msg(it).contains("language"), "code $it") }
    }
}
