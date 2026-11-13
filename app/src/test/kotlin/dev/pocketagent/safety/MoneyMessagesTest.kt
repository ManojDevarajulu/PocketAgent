package dev.pocketagent.safety

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoneyMessagesTest {
    @Test fun bankHeaderIsMoney() = assertTrue(MoneyMessages.isMoney("VM-HDFCBK", "Hello"))
    @Test fun headerMatchingBlockedPatternIsMoney() = assertTrue(MoneyMessages.isMoney("AX-PAYTMB", "Hi"))
    @Test fun headerWithSuffixIsMoney() = assertTrue(MoneyMessages.isMoney("VM-HDFCBK-S", "Hello"))
    @Test fun transactionWordsAreMoney() {
        assertTrue(MoneyMessages.isMoney("Mom", "Rs 5,000 debited from A/c XX1234"))
        assertTrue(MoneyMessages.isMoney(null, "Avl Bal: 12,300"))
        assertTrue(MoneyMessages.isMoney(null, "आपके खाते से 500 रुपये डेबिट"))
    }
    @Test fun casualMoneyTalkIsNotMoney() = assertFalse(MoneyMessages.isMoney("Rahul", "I'll pay you back tomorrow"))
    @Test fun ordinaryChatIsNotMoney() = assertFalse(MoneyMessages.isMoney("Mom", "Dinner at 8?"))
}
