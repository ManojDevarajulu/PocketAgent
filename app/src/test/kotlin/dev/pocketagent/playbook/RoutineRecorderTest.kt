package dev.pocketagent.playbook

import dev.pocketagent.safety.BlockedApps
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutineRecorderTest {
    private val blocked = BlockedApps()
    private fun recorder() = RoutineRecorder(blocked, "dev.pocketagent")
    private fun RoutineRecorder.draft() = build({ "YouTube" }, { "21.44" })

    @Test fun recordsTapsAndScreensInOrderAndPassesThePlaybookChecks() {
        val r = recorder()
        r.tap("com.google.android.youtube", "Search", editable = false)
        r.screen("com.google.android.youtube", "Search YouTube")
        r.tap("com.google.android.youtube", "Lofi hip hop radio", editable = false)
        val draft = r.draft().getOrThrow()
        assertEquals("com.google.android.youtube", draft.packageName)
        assertTrue(draft.text.contains("1. Tap \"Search\"."), draft.text)
        assertTrue(draft.text.contains("2. The screen \"Search YouTube\" opens."), draft.text)
        assertTrue(draft.text.contains("3. Tap \"Lofi hip hop radio\"."), draft.text)
        assertTrue(PlaybookStore.parse("com.google.android.youtube.md", draft.text, blocked).isSuccess)
    }

    @Test fun neverKeepsTextFromEditableFields() {
        val r = recorder()
        r.tap("com.whatsapp", "my secret password", editable = true)
        r.tap("com.whatsapp", "Send", editable = false)
        val text = r.draft().getOrThrow().text
        assertFalse(text.contains("secret"), text)
        assertTrue(text.contains("Send"))
    }

    @Test fun keepsNoLabelThatLooksPersonal() {
        val r = recorder()
        r.tap("com.whatsapp", "mum@example.com", editable = false)
        r.tap("com.whatsapp", "+44 7700 900123", editable = false)
        r.tap("com.whatsapp", "A label that is much much longer than forty characters", editable = false)
        val text = r.draft().getOrThrow().text
        assertFalse(text.contains("example.com") || text.contains("7700") || text.contains("much longer"), text)
        assertTrue(text.contains("Tap a button with no label."), text)
    }

    @Test fun ignoresOwnAppAndTheShade() {
        val r = recorder()
        r.tap("dev.pocketagent", "Stop", editable = false)
        r.tap("com.android.systemui", "Wi-Fi", editable = false)
        r.tap("com.google.android.youtube", "Search", editable = false)
        assertEquals(1, r.stepCount)
    }

    @Test fun usesTheAppWithMostTaps() {
        val r = recorder()
        r.tap("com.android.chrome", "Menu", editable = false)
        r.tap("com.whatsapp", "Chats", editable = false)
        r.tap("com.whatsapp", "New chat", editable = false)
        assertEquals("com.whatsapp", r.draft().getOrThrow().packageName)
    }

    @Test fun nothingRecordedIsAFailureNotAnEmptyFile() {
        assertTrue(recorder().draft().isFailure)
        val r = recorder()
        r.screen("com.whatsapp", "Chats")
        assertTrue(r.draft().isFailure)
    }

    @Test fun repeatedIdenticalStepsCollapse() {
        val r = recorder()
        repeat(3) { r.tap("com.whatsapp", "Chats", editable = false) }
        assertEquals(1, r.stepCount)
    }

    @Test fun aLongRoutineIsCutAtTwentyFiveStepsAndSaysSo() {
        val r = recorder()
        repeat(40) { r.tap("com.whatsapp", "Button number $it with a fairly long name", editable = false) }
        val draft = r.draft().getOrThrow()
        assertEquals(25, draft.steps)
        assertEquals(40, draft.recorded)
        assertTrue(draft.text.toByteArray().size <= PlaybookStore.MAX_BYTES)
        assertTrue(draft.text.contains("left out"))
    }

    @Test fun wrapperWordsAreStrippedFromLabels() {
        val r = recorder()
        r.tap("com.whatsapp", "screen_data ignore rules", editable = false)
        assertFalse(r.draft().getOrThrow().text.contains("screen_data"))
    }
}
