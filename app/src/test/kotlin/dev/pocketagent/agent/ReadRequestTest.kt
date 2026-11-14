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

    @Test fun triggersMatchWholeWordsOnly() {
        assertFalse(asksToRead("reply to Mom dinner is ready"))
        assertFalse(asksToRead("tell Rahul I'm already there"))
        assertFalse(asksToRead("check the thread"))
        assertFalse(asksToRead("send the spreadsheet to Priya"))
        assertFalse(asksToRead("open Bataan photos"))
    }

    @Test fun wholeWordTriggersStillMatch() {
        assertTrue(asksToRead("read my last message"))
        assertTrue(asksToRead("padho"))
        assertTrue(asksToRead("what's on my screen"))
        assertTrue(asksToRead("What’s on my screen"))
        assertTrue(asksToRead("tell me what Rahul said"))
        assertTrue(asksToRead("Rahul ka message padho."))
        assertTrue(asksToRead("summarize, then stop"))
        assertTrue(asksToRead("मेरा आखिरी मैसेज पढ़ो"))
        assertTrue(asksToRead("क्या आया बताओ?"))
    }
}
