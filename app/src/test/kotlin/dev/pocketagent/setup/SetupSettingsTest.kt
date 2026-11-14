package dev.pocketagent.setup

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupSettingsTest {
    private val store = FakeStore()
    private val settings = SetupSettings(store)

    @Test
    fun defaults() {
        assertFalse(settings.bubbleOn)
        assertFalse(settings.noticeAccepted)
        assertFalse(settings.microphoneAsked)
        assertNull(settings.acceptedCustomHost)
    }

    @Test
    fun eachValueRoundTrips() {
        settings.noticeAccepted = true
        settings.microphoneAsked = true
        settings.bubbleOn = true
        settings.acceptedCustomHost = "api.example.com"
        val again = SetupSettings(store)
        assertTrue(again.noticeAccepted)
        assertTrue(again.microphoneAsked)
        assertTrue(again.bubbleOn)
        assertEquals("api.example.com", again.acceptedCustomHost)
    }

    @Test
    fun talkBackDefaultsToOn() {
        assertTrue(settings.talkBack)
    }

    @Test
    fun talkBackRoundTrips() {
        settings.talkBack = false
        assertFalse(SetupSettings(store).talkBack)
        settings.talkBack = true
        assertTrue(SetupSettings(store).talkBack)
    }

    @Test
    fun settingTheHostToNullRemovesIt() {
        settings.acceptedCustomHost = "api.example.com"
        settings.acceptedCustomHost = null
        assertNull(settings.acceptedCustomHost)
        assertFalse(store.strings.containsKey("settings.customHost"))
    }

    @Test
    fun themeDefaultsToSystemAndRoundTrips() {
        val settings = SetupSettings(FakeStore())
        assertEquals("system", settings.theme)
        settings.theme = "dark"
        assertEquals("dark", settings.theme)
    }

    @Test
    fun shortcutsAreNullUntilSavedAndKeepTheirOrder() {
        val settings = SetupSettings(FakeStore())
        assertEquals(null, settings.shortcuts)
        settings.shortcuts = listOf("Take me home", "Read this screen")
        assertEquals(listOf("Take me home", "Read this screen"), settings.shortcuts)
        settings.shortcuts = emptyList()
        assertEquals(emptyList(), settings.shortcuts)
    }
}
