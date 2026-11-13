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
}
