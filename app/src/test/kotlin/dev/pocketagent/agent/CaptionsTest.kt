package dev.pocketagent.agent

import dev.pocketagent.device.Direction
import dev.pocketagent.device.node
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CaptionsTest {
    private fun tap(label: String?, password: Boolean = false) =
        Captions.forAction(ToolCall.Tap("a1", null, null, false), node("a1", text = label, isPassword = password))

    @Test
    fun captionUsesTargetLabel() {
        assertEquals("Tapping Add to cart", tap("Add to cart"))
    }

    @Test
    fun longLabelIsShortened() {
        assertEquals("Tapping " + "x".repeat(30) + "…", tap("x".repeat(50)))
    }

    @Test
    fun labelOfExactlyTheLimitIsNotShortened() {
        assertEquals("Tapping " + "x".repeat(30), tap("x".repeat(30)))
    }

    @Test
    fun passwordFieldIsNeverNamed() {
        val caption = Captions.forAction(ToolCall.TypeText("a1", "hunter2"), node("a1", text = "Password", isPassword = true))
        assertEquals("Typing into a field", caption)
        assertEquals("Tapping an item", tap("Password", password = true))
    }

    @Test
    fun typingNamesTheField() {
        assertEquals("Typing into Message", Captions.forAction(ToolCall.TypeText("a1", "hi"), node("a1", text = "Message")))
        assertEquals("Searching in Search", Captions.forAction(ToolCall.TypeText("a1", "milk", submit = true), node("a1", text = "Search")))
    }

    @Test
    fun missingLabelOrTargetFallsBackToGenericWords() {
        assertEquals("Tapping an item", tap(null))
        assertEquals("Tapping an item", tap("   "))
        assertEquals("Tapping an item", Captions.forAction(ToolCall.Tap(null, 5, 5, false), null))
        assertEquals("Typing into a field", Captions.forAction(ToolCall.TypeText("a1", "hi"), null))
    }

    @Test
    fun controlCharactersInLabelsBecomeSpaces() {
        val caption = tap("Pay\nnow please")
        assertEquals("Tapping Pay now please", caption)
        assertFalse(caption.contains('\n'))
    }

    @Test
    fun shorteningNeverSplitsASurrogatePair() {
        val caption = tap("x".repeat(29) + "😀" + "tail")
        assertEquals(caption, String(caption.toByteArray(Charsets.UTF_8), Charsets.UTF_8), "no lone surrogate")
    }

    @Test
    fun fixedCaptions() {
        assertEquals("Scrolling down", Captions.forAction(ToolCall.Scroll(Direction.DOWN, null), null))
        assertEquals("Scrolling left", Captions.forAction(ToolCall.Scroll(Direction.LEFT, "a1"), null))
        assertEquals("Going back", Captions.forAction(ToolCall.Back, null))
        assertEquals("Going home", Captions.forAction(ToolCall.Home, null))
        assertEquals("Taking a screenshot", Captions.forAction(ToolCall.Screenshot, null))
        assertEquals("Waiting", Captions.forAction(ToolCall.Wait(3), null))
        assertEquals("Asking you", Captions.forAction(ToolCall.AskUser("Which Mom?"), null))
        assertEquals("Finishing", Captions.forAction(ToolCall.Done("ok", true), null))
    }

    @Test
    fun openingAnAppCleansAndShortensTheModelSuppliedName() {
        assertEquals("Opening WhatsApp", Captions.forAction(ToolCall.OpenApp("WhatsApp"), null))
        assertEquals("Opening " + "n".repeat(30) + "…", Captions.forAction(ToolCall.OpenApp("n".repeat(80)), null))
        assertEquals("Opening a b", Captions.forAction(ToolCall.OpenApp("a\nb"), null))
        assertEquals("Opening an app", Captions.forAction(ToolCall.OpenApp("   "), null))
    }

    @Test
    fun shortcutCaptionsAreFixedWordsWithoutTheModelsText() {
        assertEquals("Starting directions", Captions.forAction(ToolCall.Directions("secret place"), null))
        assertEquals("Opening the phone app", Captions.forAction(ToolCall.Dial("5551234"), null))
        assertEquals("Writing a text message", Captions.forAction(ToolCall.TextMessage("5551234", "secret"), null))
        assertEquals("Searching the web", Captions.forAction(ToolCall.WebSearch("secret"), null))
        assertEquals("Setting an alarm for 06:30", Captions.forAction(ToolCall.SetAlarm(6, 30), null))
        assertEquals("Starting a timer", Captions.forAction(ToolCall.SetTimer(600), null))
        assertEquals("Opening a web link", Captions.forAction(ToolCall.OpenUrl("https://secret.example"), null))
        assertEquals("Adding a calendar event", Captions.forAction(ToolCall.AddEvent("secret", java.time.LocalDateTime.of(2026, 1, 1, 9, 0), 30), null))
    }
}
