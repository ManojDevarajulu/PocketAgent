package dev.pocketagent.llm

import org.junit.Test
import kotlin.test.assertEquals

class TokenUsageTest {
    @Test
    fun plusAddsEachField() {
        val sum = TokenUsage(1, 2, 3) + TokenUsage(10, 20, 30)
        assertEquals(TokenUsage(11, 22, 33), sum)
    }
}
