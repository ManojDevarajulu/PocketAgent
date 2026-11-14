package dev.pocketagent.device

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenDiffTest {
    private fun screen(letter: Char, vararg lines: String, header: String = "<screen_data app=\"com.x\" label=\"X\">") =
        buildString {
            appendLine(header)
            lines.forEachIndexed { i, l -> appendLine(l.replace("@", "[$letter$i]")) }
            append("</screen_data>")
        }

    private val statics = Array(12) { "@ text \"Row number $it of the list\"" }

    @Test fun firstScreenAndChangedAppAreSentWhole() {
        val now = screen('b', *statics, "@ button \"Go\" clickable")
        assertNull(ScreenDiff.shorten(null, now))
        assertNull(ScreenDiff.shorten(screen('a', *statics, "@ button \"Go\" clickable", header = "<screen_data app=\"com.y\" label=\"Y\">"), now))
    }

    @Test fun unchangedTextIsOmittedButEveryTappableNodeKeepsItsFreshId() {
        val before = screen('a', *statics, "@ button \"Go\" clickable", "@ input \"Search\" editable")
        val now = screen('b', *statics, "@ button \"Go\" clickable", "@ input \"Search\" editable", "@ text \"New toast\"")
        val short = assertNotNull(ScreenDiff.shorten(before, now))
        assertTrue(short.contains("[b12] button \"Go\" clickable"), short)
        assertTrue(short.contains("[b13] input \"Search\" editable"), short)
        assertTrue(short.contains("[b14] text \"New toast\""), short)
        assertTrue(short.contains("... 12 unchanged text lines omitted"), short)
        assertTrue(short.startsWith("<screen_data app=\"com.x\" label=\"X\" diff=\"true\">"), short)
        assertTrue(short.length < now.length)
    }

    @Test fun changedTextIsKept() {
        val before = screen('a', *statics, "@ text \"Count 1\"")
        val now = screen('b', *statics, "@ text \"Count 2\"")
        val short = assertNotNull(ScreenDiff.shorten(before, now))
        assertTrue(short.contains("[b12] text \"Count 2\""), short)
        assertTrue(!short.contains("Count 1"), short)
    }

    @Test fun aMostlyNewScreenIsSentWhole() {
        val before = screen('a', *statics)
        val now = screen('b', *Array(12) { "@ text \"Something else entirely $it\"" })
        assertNull(ScreenDiff.shorten(before, now))
    }

    @Test fun aSmallSavingIsNotWorthIt() {
        val before = screen('a', "@ text \"Title\"", "@ button \"A\" clickable", "@ button \"B\" clickable", "@ button \"C\" clickable")
        val now = screen('b', "@ text \"Title\"", "@ button \"A\" clickable", "@ button \"B\" clickable", "@ button \"C\" clickable")
        assertNull(ScreenDiff.shorten(before, now))
    }

    @Test fun aRepeatedLineIsOnlyOmittedAsOftenAsItWasSeen() {
        val before = screen('a', *statics, "@ text \"Same\"")
        val now = screen('b', *statics, "@ text \"Same\"", "@ text \"Same\"")
        val short = assertNotNull(ScreenDiff.shorten(before, now))
        assertEquals(1, Regex("\\[b13\\] text \"Same\"").findAll(short).count(), short)
    }

    @Test fun aTruncatedScreenIsSentWhole() {
        val before = screen('a', *statics)
        val now = screen('b', *statics, "[truncated: 40 more nodes]")
        assertNull(ScreenDiff.shorten(before, now))
    }
}
