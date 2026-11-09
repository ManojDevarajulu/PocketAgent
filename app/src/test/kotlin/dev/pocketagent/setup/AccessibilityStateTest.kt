package dev.pocketagent.setup

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessibilityStateTest {
    private val pkg = "dev.pocketagent"
    private val cls = "dev.pocketagent.android.PocketAccessibilityService"
    private fun on(s: String?) = AccessibilityState.isEnabled(s, pkg, cls)

    @Test fun fullComponentNameMatches() = assertTrue(on("dev.pocketagent/dev.pocketagent.android.PocketAccessibilityService"))
    @Test fun shortRelativeNameMatches() = assertTrue(on("dev.pocketagent/.android.PocketAccessibilityService"))
    @Test fun caseDoesNotMatter() = assertTrue(on("DEV.PocketAgent/.Android.pocketaccessibilityservice"))
    @Test fun otherServicesInTheListAreIgnored() =
        assertTrue(on("a.b/.A:dev.pocketagent/.android.PocketAccessibilityService:c.d/.C"))
    @Test fun aLookalikeDoesNotMatch() {
        assertFalse(on("dev.pocketagent.fake/dev.pocketagent.android.PocketAccessibilityService"))
        assertFalse(on("dev.pocketagent/dev.pocketagent.android.PocketAccessibilityServiceX"))
    }
    @Test fun nullBlankAndEmptyAreFalse() {
        assertFalse(on(null)); assertFalse(on("")); assertFalse(on("   "))
    }
}
