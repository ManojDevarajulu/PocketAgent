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

    @Test fun englishDebitAndCreditStems() {
        assertTrue(MoneyMessages.isMoney(null, "Debit alert: Rs 500 at XYZ"))
        assertTrue(MoneyMessages.isMoney(null, "Credit card spent Rs 2,000 at Amazon"))
    }
    @Test fun spentIsMoney() = assertTrue(MoneyMessages.isMoney(null, "Spent Rs 450 on card XX1234"))
    @Test fun withdrawnIsMoney() = assertTrue(MoneyMessages.isMoney(null, "Cash withdrawn at ATM"))
    @Test fun hindiStemIsMoney() = assertTrue(MoneyMessages.isMoney(null, "आपका खाता में 500 जमा हुए"))
    @Test fun upiIsMoney() = assertTrue(MoneyMessages.isMoney(null, "UPI txn"))
    @Test fun upiAloneIsMoney() = assertTrue(MoneyMessages.isMoney(null, "Sent via UPI"))
    @Test fun emiAloneIsMoney() = assertTrue(MoneyMessages.isMoney(null, "Your EMI is due"))
    @Test fun blockedPatternHeaderIsMoney() {
        assertTrue(MoneyMessages.isMoney("XX-WALLETS", "Hi"))
        assertTrue(MoneyMessages.isMoney("XX-BANKXYZ", "Hi"))
    }
    @Test fun widenedBankNameIsMoney() = assertTrue(MoneyMessages.isMoney("VK-AUBANK", "Hi"))
    @Test fun lowercaseHeaderIsMoney() = assertTrue(MoneyMessages.isMoney("vm-hdfcbk", "Hi"))
    @Test fun unknownHeaderIsNotMoney() = assertFalse(MoneyMessages.isMoney("VM-RANDOM", "See you soon"))
    @Test fun nullSenderOrdinaryTextIsNotMoney() = assertFalse(MoneyMessages.isMoney(null, "See you soon"))
    @Test fun headerShapeGuard() {
        assertFalse(MoneyMessages.isMoney("VM-HD", "Hi"))
        assertFalse(MoneyMessages.isMoney("VM-HDFCBKXXXX", "Hi"))
        assertFalse(MoneyMessages.isMoney("V-HDFCBK", "Hi"))
    }
    @Test fun shortStemsNeedWholeWord() {
        assertFalse(MoneyMessages.isMoney("Emily", "Emily says hi"))
        assertFalse(MoneyMessages.isMoney("Upinder", "Upinder: lunch?"))
    }
    @Test fun ordinaryChatIsKept() {
        for (t in listOf(
            "I spent the whole weekend at Mom's", "उसके खातिर कुछ करो", "Emily says hi", "Upinder: lunch?",
            "I'll pay you back tomorrow", "Dinner at 8?",
        )) assertFalse(MoneyMessages.isMoney("Friend", t), t)
    }
    @Test fun rsNeedsAnAmount() {
        assertTrue(MoneyMessages.isMoney(null, "Rs 450 at the cafe"))
        assertFalse(MoneyMessages.isMoney(null, "Rs. is short for rupees"))
    }
    @Test fun hindiAccountForms() {
        assertTrue(MoneyMessages.isMoney(null, "खाताधारक सूचना"))
        assertTrue(MoneyMessages.isMoney(null, "सभी खातों की जानकारी"))
    }

    @Test fun moneyNameWholeWordsAndHeaders() {
        assertTrue(MoneyMessages.isMoneyName("HDFC Bank alerts"))
        assertTrue(MoneyMessages.isMoneyName("VM-HDFCBK"))
        assertTrue(MoneyMessages.isMoneyName("Credit card"))
    }
    @Test fun moneyNameKeepsPeopleWhoseNamesOnlyStartLikeMoneyWords() {
        assertFalse(MoneyMessages.isMoneyName("Bankey"))
        assertFalse(MoneyMessages.isMoneyName("Bankim"))
        assertFalse(MoneyMessages.isMoneyName("Loanna"))
        assertFalse(MoneyMessages.isMoneyName("Balancer"))
        assertFalse(MoneyMessages.isMoneyName("Family"))
        assertFalse(MoneyMessages.isMoneyName(null))
    }
}
