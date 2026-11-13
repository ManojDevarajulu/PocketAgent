package dev.pocketagent.setup

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListenerEnabledTest {
    private val me = "dev.pocketagent/dev.pocketagent.android.PocketNotificationListener"

    @Test fun nullAndEmptyAreOff() {
        assertFalse(isListenerEnabled(null, me))
        assertFalse(isListenerEnabled("", me))
    }

    @Test fun exactMatchIsOn() = assertTrue(isListenerEnabled(me, me))

    @Test fun matchAmongSeveralIsOn() =
        assertTrue(isListenerEnabled("com.a/com.a.L:$me:com.b/com.b.M", me))

    @Test fun differentClassInSamePackageIsOff() =
        assertFalse(isListenerEnabled("dev.pocketagent/dev.pocketagent.android.OtherListener", me))

    @Test fun substringOfAnotherComponentIsOff() {
        assertFalse(isListenerEnabled("x.dev.pocketagent/dev.pocketagent.android.PocketNotificationListenerX", me))
        assertFalse(isListenerEnabled("evil/$me", me))
    }
}
