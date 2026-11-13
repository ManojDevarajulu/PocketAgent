package dev.pocketagent.device

import org.junit.Test
import kotlin.test.assertEquals

class MessageLineTest {
    @Test fun theOwnersMessageIsMarkedAsMe() {
        assertEquals("Me: on my way", messageLine(sender = null, fromOwner = true, text = "on my way"))
        // The owner's message may carry the owner's own name; it is still marked as Me, not as that name.
        assertEquals("Me: on my way", messageLine(sender = "Manu", fromOwner = true, text = "on my way"))
    }

    @Test fun someoneElsesMessageKeepsTheirName() {
        assertEquals("Rahul: are you coming?", messageLine(sender = "Rahul", fromOwner = false, text = "are you coming?"))
    }

    @Test fun aSenderWithNoNameGivesJustTheText() {
        assertEquals("are you coming?", messageLine(sender = " ", fromOwner = false, text = "are you coming?"))
        assertEquals("are you coming?", messageLine(sender = null, fromOwner = false, text = "are you coming?"))
    }
}
