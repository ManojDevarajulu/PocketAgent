package dev.pocketagent.bridge

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PairingTokenTest {
    @Test fun tokensAreLongHexAndDifferEachTime() {
        val a = PairingToken.generate()
        assertEquals(48, a.length)
        assertTrue(PairingToken.looksValid(a))
        assertNotEquals(a, PairingToken.generate())
    }

    @Test fun anythingElseIsNotAToken() {
        assertFalse(PairingToken.looksValid(null))
        assertFalse(PairingToken.looksValid(""))
        assertFalse(PairingToken.looksValid("abc"))
        assertFalse(PairingToken.looksValid("Z".repeat(48)))
    }
}
