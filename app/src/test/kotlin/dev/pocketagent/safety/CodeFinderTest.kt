package dev.pocketagent.safety

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class CodeFinderTest {
    @Test fun findsOtpNextToTriggerWord() = assertEquals("482913", CodeFinder.find("482913 is your OTP for login. Do not share."))
    @Test fun findsDashedCode() = assertEquals("123456", CodeFinder.find("Your verification code: 123-456"))
    @Test fun findsHindiTrigger() = assertEquals("4829", CodeFinder.find("आपका ओटीपी 4829 है"))
    @Test fun ignoresAmountsPhonesAndDates() {
        assertNull(CodeFinder.find("Rs 5000 debited"))
        assertNull(CodeFinder.find("Call me on 9876543210"))
        assertNull(CodeFinder.find("See you on 12-11-2026"))
    }
    @Test fun ignoresDigitsWithoutTriggerWord() = assertNull(CodeFinder.find("Room 4821 at 5pm"))
    @Test fun maskKeepsTheRest() = assertEquals("•••••• is your OTP", CodeFinder.mask("482913 is your OTP"))
    @Test fun spacedDigitsAreMaskedWhenNextToTriggerWord() = assertEquals("OTP ••••••", CodeFinder.mask("OTP 4 8 2 9 1 3"))
    @Test fun triggeredInputsStillRejectNonCodes() {
        assertNull(CodeFinder.find("OTP for Rs 5000"))
        assertNull(CodeFinder.find("code 9876543210"))
        assertNull(CodeFinder.find("OTP 12-11-2026"))
    }
    @Test fun hindiDigitsAreFoundAndMasked() {
        assertEquals("4829", CodeFinder.find("आपका ओटीपी ४८२९ है"))
        assertEquals("आपका ओटीपी •••••• है", CodeFinder.mask("आपका ओटीपी ४८२९ है"))
    }
    @Test fun rsInsideAWordIsNotCurrency() = assertEquals("users •••••• OTP", CodeFinder.mask("users 482913 OTP"))

    // ---- a trigger word in another field of the same notification ----

    @Test fun triggerInOneFieldMasksCodesInEveryField() {
        assertEquals(listOf("Login OTP", "Use •••••• within 5 min"), CodeFinder.maskAll(listOf("Login OTP", "Use 4829 within 5 min")))
        assertEquals(
            listOf("Rahul: what's the OTP?", "Rahul: ••••••"),
            CodeFinder.maskAll(listOf("Rahul: what's the OTP?", "Rahul: 482913")),
        )
    }
    @Test fun maskAllLeavesDigitsAloneWithoutAnyTrigger() {
        assertEquals(listOf("Room 4821 at 5pm", "See you there"), CodeFinder.maskAll(listOf("Room 4821 at 5pm", "See you there")))
    }
    @Test fun maskAllStillRejectsAmountsAndDates() {
        assertEquals(
            listOf("Login OTP", "Rs 5000 debited", "See you on 12-11-2026"),
            CodeFinder.maskAll(listOf("Login OTP", "Rs 5000 debited", "See you on 12-11-2026")),
        )
    }
    @Test fun findInLooksAcrossFields() {
        assertEquals("4829", CodeFinder.findIn(listOf("Login OTP", "Use 4829 within 5 min")))
        assertNull(CodeFinder.findIn(listOf("Rahul", "Room 4821 at 5pm")))
        assertNull(CodeFinder.findIn(listOf("Login OTP", "Rs 5000 debited")))
    }

    // ---- "123 456" codes ----

    @Test fun findsCodesSplitByOneSpace() {
        assertEquals("123456", CodeFinder.find("123 456 is your Instagram code"))
        assertEquals("12345678", CodeFinder.find("Your code: 1234 5678"))
        assertEquals("1234567", CodeFinder.find("OTP 123 4567"))
        assertEquals("1234567", CodeFinder.find("OTP 1234 567"))
        assertEquals("•••••• is your Instagram code", CodeFinder.mask("123 456 is your Instagram code"))
    }
    @Test fun phoneNumbersSplitBySpacesAreNotCodes() {
        assertNull(CodeFinder.find("code 98765 43210"))
        assertNull(CodeFinder.find("OTP help: call +91 98765 43210"))
        assertEquals("code 98765 43210", CodeFinder.mask("code 98765 43210"))
        assertNull(CodeFinder.find("OTP 12-11-2026"))
    }
    @Test fun aShortNumberAfterTheCodeDoesNotHideIt() = assertEquals("4829", CodeFinder.find("OTP 4829 5 min left"))
}
