package dev.pocketagent.safety

import java.util.regex.Pattern

/**
 * Recognises bank and payment messages so they never reach the AI provider. A message counts as money when the
 * sender is a bank-style header (two letters, a dash, 3 to 9 letters or digits, optional -S/-T suffix) whose name part
 * is a known bank or matches the blocked-app words, or when the text holds a transaction word. Matching is by
 * word start, so over-dropping is accepted.
 */
object MoneyMessages {
    private val header: Pattern = Pattern.compile("""^[A-Z]{2}-([A-Z0-9]{3,9})(?:-[ST])?$""")

    private val headerNames = wordStartPattern(
        BlockedApps.DEFAULT_PATTERNS + listOf(
            "HDFCBK", "ICICIB", "SBIINB", "SBIPSG", "AXISBK", "KOTAKB", "PAYTMB", "PNBSMS", "BOBTXN", "CANBNK",
            "IDFCFB", "YESBNK", "INDUSB", "AMEXIN", "CITIBK", "SCBANK", "PHONPE", "GPAY", "AIRBNK", "JIOPBS",
        ),
    )

    private val words = wordStartPattern(
        listOf(
            "debited", "credited", "a/c", "acct", "account no", "avl bal", "available balance", "balance", "upi",
            "card ending", "txn", "transaction", "neft", "imps", "rtgs", "emi",
            "डेबिट", "क्रेडिट", "खाते", "शेष",
        ),
    )

    fun isMoney(sender: String?, text: String): Boolean {
        if (sender != null) {
            val m = header.matcher(matchForm(sender).trim())
            if (m.matches() && headerNames.find(m.group(1)!!)) return true
        }
        return words.find(text)
    }
}
