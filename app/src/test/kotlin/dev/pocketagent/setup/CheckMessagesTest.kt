package dev.pocketagent.setup

import dev.pocketagent.llm.CheckResult
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckMessagesTest {
    private fun msg(r: CheckResult) = CheckMessages.messageFor(r).lowercase()

    @Test fun worksSaysItCanUseTools() = assertTrue(msg(CheckResult.Works).contains("tool"))
    @Test fun noToolCallsSaysToPickAnotherModel() {
        val m = msg(CheckResult.NoToolCalls)
        assertTrue(m.contains("tool") && m.contains("another model"))
    }
    @Test fun badKeyMentionsTheKey() = assertTrue(msg(CheckResult.BadKey).contains("key"))
    @Test fun unreachableMentionsTheConnection() = assertTrue(msg(CheckResult.Unreachable).contains("connection"))
    @Test fun otherShowsTheProvidersMessage() =
        assertTrue(CheckMessages.messageFor(CheckResult.Other("  429 rate limited ")).contains("429 rate limited"))
    @Test fun otherWithABlankMessageFallsBack() = assertTrue(CheckMessages.messageFor(CheckResult.Other("  ")).isNotBlank())
    @Test fun theFixedMessagesNeverMentionAKeyValue() {
        listOf(CheckResult.Works, CheckResult.NoToolCalls, CheckResult.BadKey, CheckResult.Unreachable).forEach {
            val m = CheckMessages.messageFor(it)
            assertFalse(m.contains("sk-") || m.contains("Bearer"))
        }
    }
}
