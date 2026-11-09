package dev.pocketagent.ui

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class NoticeTextTest {
    private val xml = File("src/main/res/values/strings.xml").readText()

    private fun string(name: String): String {
        val match = Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(xml)
        return requireNotNull(match) { "missing string $name" }.groupValues[1]
    }

    @Test
    fun noticeCoversTheFourPoints() {
        val body = string("notice_body").lowercase()
        for (word in listOf("directly", "nowhere else", "stored", "ask", "long-press")) {
            assertTrue(word in body, "notice_body should contain \"$word\"")
        }
        for (name in listOf("notice_title", "notice_accept")) assertTrue(string(name).isNotBlank())
    }

    @Test
    fun customHostNoticeTakesTheHost() {
        assertTrue("%1\$s" in string("notice_custom_host"))
    }

    @Test
    fun micNoticeSaysTheRecogniserMayUseTheCloud() {
        val mic = string("notice_mic").lowercase()
        assertTrue("cloud" in mic || "online" in mic, mic)
    }
}
