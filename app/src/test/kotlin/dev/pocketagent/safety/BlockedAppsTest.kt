package dev.pocketagent.safety

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockedAppsTest {
    private val blocked = BlockedApps()

    @Test
    fun blocksListedPackage() {
        assertTrue(blocked.isBlocked("com.phonepe.app", null))
    }

    @Test
    fun blocksSensitiveSystemApps() = listOf(
        "com.android.settings", "com.android.systemui", "com.google.android.packageinstaller",
        "com.android.packageinstaller", "com.google.android.permissioncontroller", "com.android.permissioncontroller",
    ).forEach { assertTrue(blocked.isBlocked(it, null), it) }

    @Test
    fun blocksByLabelPattern() {
        assertTrue(blocked.isBlocked("com.example.x", "HDFC Bank"))
        assertTrue(blocked.isBlocked("com.example.y", "bank of india"))
    }

    @Test
    fun blocksBySegmentPattern() {
        assertTrue(blocked.isBlocked("com.paytm.business", null))
    }

    @Test
    fun doesNotBlockDisplayOrSwiggy() {
        assertFalse(blocked.isBlocked("com.android.display", "Display"))
        assertFalse(blocked.isBlocked("in.swiggy.android", "Swiggy"))
        assertFalse(blocked.isBlocked("com.whatsapp", "WhatsApp"))
    }

    @Test
    fun blocksKnownAppNamesWhenOnlyTheNameIsKnown() = listOf("PhonePe", "1Password", "Settings", " bitwarden ", "Google Pay")
        .forEach { assertTrue(blocked.isBlocked("", it), it) }

    @Test
    fun emptyPatternListBlocksNothingByPattern() {
        val none = BlockedApps(packages = emptySet(), patterns = emptyList(), names = emptySet())
        assertFalse(none.isBlocked("com.example", "Hello"))
    }

    @Test
    fun blocksLabelsDisguisedWithInvisibleOrFullwidthCharacters() {
        assertTrue(blocked.isBlocked("com.example.x", "HDFC Ba​nk"))
        assertTrue(blocked.isBlocked("com.example.y", "Ｐａｙtm"))
        assertTrue(blocked.isBlocked("", "Phone­Pe"))
    }

    @Test
    fun blocksBankingAppsWhoseNamesLackBankOrPay() = listOf(
        "com.sbi.lotusintouch", "com.sbi.SBIFreedomPlus", "com.snapwork.hdfc", "com.axis.mobile", "com.msf.kbank.mobile",
        "com.kotak811mobilebankingapp.instantsavingsbankaccount", "com.csam.icici.bank.imobile", "com.dreamplug.androidapp",
    ).forEach { assertTrue(blocked.isBlocked(it, null), it) }

    @Test
    fun blocksBankingAppsByNameWhenOnlyTheNameIsKnown() = listOf(
        "YONO SBI", "YONO", "yono lite sbi", "Axis Mobile", "Kotak 811", "iMobile", "CRED", "Kotak", "HDFC", "ICICI", "SBI", "Axis",
    ).forEach { assertTrue(blocked.isBlocked("", it), it) }

    @Test
    fun bankShortNamesBlockOnlyAsTheWholeName() = listOf(
        "Credit Karma Notes", "Axis Notes", "Sbitube", "Credo", "Taxis Now", "Yonder",
    ).forEach { assertFalse(blocked.isBlocked("com.example.app", it), it) }
}
