package dev.pocketagent.safety

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RiskyLabelsTest {
    @Test
    fun matchesEnglishTerms() = listOf(
        "Pay now", "PLACE ORDER", "Send", "Delete chat", "Payment", "Buy now", "Confirm order",
        "Transfer", "Remove item", "Post", "Uninstall app", "(Send)", "Tap to send!",
    ).forEach { assertTrue(RiskyLabels.isRisky(it), it) }

    @Test
    fun installLabelIsRisky() {
        assertTrue(RiskyLabels.isRisky("Install"))
    }

    @Test
    fun ignoresSafeLabels() = listOf(
        "Search", "Spotify", "Display", "Repay", "Compost", "Add to cart", "Playlist", null, "",
    ).forEach { assertFalse(RiskyLabels.isRisky(it), "$it") }

    @Test
    fun matchesHindiTerms() = listOf(
        "अभी भुगतान करें", "भेजें", "खरीदें", "हटाएं", "ऑर्डर करें", "पुष्टि करें",
    ).forEach { assertTrue(RiskyLabels.isRisky(it), it) }
}
