package dev.pocketagent.safety

import java.util.regex.Pattern

/**
 * Finds and hides one-time verification codes in text. A code is 4 to 8 digits (one inner dash, one inner space
 * between two groups of 3 or 4 digits such as "123 456", or single spaces between single digits) and counts only
 * when the text also holds a trigger word such as "OTP". Longer digit runs and spaced groups of phone numbers
 * ("98765 43210"), amounts and dd-mm-yyyy dates are not codes. Text is compared in [matchForm], so [mask] returns
 * the normalised text.
 *
 * One notification has several fields (title, chat name, lines). [maskAll] and [findIn] treat them as one text for
 * the trigger: a trigger word in any field makes codes in every field count, so "Login OTP" as a title hides the
 * code in "Use 4829 within 5 min".
 */
object CodeFinder {
    const val MASK = "••••••"

    private val triggers = wordStartPattern(
        listOf(
            "otp", "code", "verification", "verify", "passcode", "pin", "one time password", "do not share", "don't share",
            "ओटीपी", "कोड", "सत्यापन",
        ),
    )

    private val shape: Pattern = Pattern.compile(
        """(?<!\p{N})(?:\p{Nd}{3,4} \p{Nd}{3,4}|\p{Nd}(?: \p{Nd}){3,7}|\p{Nd}{2,4}-\p{Nd}{2,4}|\p{Nd}{4,8})(?!\p{N})""",
    )
    private val currencyBefore: Pattern = Pattern.compile("""(?<![\p{L}\p{N}])(?:rs\.?|inr|₹|\$)\s*$""", Pattern.CASE_INSENSITIVE)

    fun find(text: String): String? = findIn(listOf(text))

    fun mask(text: String): String = maskAll(listOf(text)).single()

    /** The first code in [fields], in order, when any field holds a trigger word. */
    fun findIn(fields: List<String>): String? {
        val forms = fields.map(::matchForm)
        if (forms.none { triggers.find(it) }) return null
        for (form in forms) {
            val r = codeRanges(form).firstOrNull() ?: continue
            return form.substring(r.first, r.last + 1).filter(Char::isDigit).map { Character.digit(it, 10) }.joinToString("")
        }
        return null
    }

    /** Every field in [matchForm], with codes masked in all of them when any field holds a trigger word. */
    fun maskAll(fields: List<String>): List<String> {
        val forms = fields.map(::matchForm)
        if (forms.none { triggers.find(it) }) return forms
        return forms.map(::maskShapes)
    }

    private fun maskShapes(form: String): String {
        val sb = StringBuilder()
        var last = 0
        for (r in codeRanges(form)) {
            sb.append(form, last, r.first).append(MASK)
            last = r.last + 1
        }
        return sb.append(form, last, form.length).toString()
    }

    private fun codeRanges(form: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        val m = shape.matcher(form)
        while (m.find()) {
            val digits = m.group().count(Char::isDigit)
            if (digits !in 4..8) continue
            if (joinedToDigits(form, m.start(), m.end())) continue
            if (spacedNextToGroup(form, m.start(), m.end())) continue
            if (currencyBefore.matcher(form.substring(0, m.start())).find()) continue
            out += m.start() until m.end()
        }
        return out
    }

    /** True for date shapes: a dash or slash plus a digit on either side of the run. */
    private fun joinedToDigits(s: String, start: Int, end: Int): Boolean {
        val before = start >= 2 && s[start - 1] in "-/" && s[start - 2].isDigit()
        val after = end + 1 < s.length && s[end] in "-/" && s[end + 1].isDigit()
        return before || after
    }

    /**
     * True when one space joins the run to another group of 3 or more digits, as in "98765 43210" or
     * "+91 98765 43210": that is a phone number. A short number after a code ("OTP 4829 5 min") does not count.
     */
    private fun spacedNextToGroup(s: String, start: Int, end: Int): Boolean {
        var before = 0
        if (start >= 2 && s[start - 1] == ' ') {
            var i = start - 2
            while (i >= 0 && s[i].isDigit()) { before++; i-- }
        }
        var after = 0
        if (end + 1 < s.length && s[end] == ' ') {
            var i = end + 1
            while (i < s.length && s[i].isDigit()) { after++; i++ }
        }
        return before >= 3 || after >= 3
    }
}
