package dev.pocketagent.agent

import org.junit.Test
import kotlin.test.assertEquals

class YesNoTest {
    private val yes = listOf("yes", "yeah", "yep", "yup", "sure", "ok", "okay", "confirm", "do it", "go ahead", "send it", "haan", "haa", "ha", "हाँ", "हां", "जी हाँ")
    private val no = listOf("no", "nope", "cancel", "stop", "don't", "do not", "wait", "nahi", "nahin", "नहीं", "ना")
    private val read = listOf("read it", "read", "what does it say", "padho", "पढ़ो")

    @Test fun everyYesEntryIsYes() {
        for (e in yes) for (v in listOf(e, e.uppercase(), "$e.", "$e!", "$e।", "  $e  ")) assertEquals(Answer.YES, YesNo.parse(v), v)
    }

    @Test fun everyNoEntryIsNo() {
        for (e in no) for (v in listOf(e, e.uppercase(), "$e.", "$e!", "$e।")) assertEquals(Answer.NO, YesNo.parse(v), v)
        assertEquals(Answer.NO, YesNo.parse("don’t"))
    }

    @Test fun readEntriesAreReadNeverYes() {
        for (e in read) assertEquals(Answer.READ, YesNo.parse(e), e)
    }

    @Test fun yesPlusExtraWordsIsUnclear() {
        for (v in listOf("yes send it to everyone", "yes no", "not yes", "yes but wait")) assertEquals(Answer.UNCLEAR, YesNo.parse(v), v)
    }

    @Test fun blankAndNullAreUnclear() {
        for (v in listOf(null, "", "   ", "...")) assertEquals(Answer.UNCLEAR, YesNo.parse(v))
    }
}
