package dev.pocketagent.agent

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadRequestTest {
    private val triggers = listOf("read", "tell me", "what does", "what did", "what's on", "summarize", "summarise", "padh", "padho", "batao", "bata", "सुनाओ", "पढ़ो", "बताओ")

    @Test fun eachTriggerWordAsksToRead() {
        for (t in triggers) assertTrue(asksToRead("please $t my last message"), t)
        assertTrue(asksToRead("read my last message"))
    }

    @Test fun replyAndSendDoNotAskToRead() {
        assertFalse(asksToRead("reply to Rahul I'm on my way"))
        assertFalse(asksToRead("send hi to Mom"))
    }

    @Test fun caseInsensitive() {
        assertTrue(asksToRead("READ my messages"))
        assertTrue(asksToRead("Tell Me what came"))
    }
}
