package dev.pocketagent.safety

import java.util.regex.Pattern

/**
 * Finds and hides one-time verification codes in text. A code is 4 to 8 digits (one inner dash, or single
 * spaces between single digits) and counts only when the text also holds a trigger word such as "OTP".
 * Longer digit runs (phone numbers), amounts and dd-mm-yyyy dates are not codes. Text is compared in [matchForm],
 * so [mask] returns the normalised text.
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
        """(?<!\p{N})(?:\p{Nd}(?: \p{Nd}){3,7}|\p{Nd}{2,4}-\p{Nd}{2,4}|\p{Nd}{4,8})(?!\p{N})""",
    )
    private val currencyBefore: Pattern = Pattern.compile("""(?<![\p{L}\p{N}])(?:rs\.?|inr|₹|\$)\s*$""", Pattern.CASE_INSENSITIVE)

    fun find(text: String): String? {
        val form = matchForm(text)
        if (!triggers.find(form)) return null
        return codeRanges(form).firstOrNull()?.let { form.substring(it.first, it.last + 1).filter(Char::isDigit).map { Character.digit(it, 10) }.joinToString("") }
    }

    fun mask(text: String): String {
        val form = matchForm(text)
        if (!triggers.find(form)) return form
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
}
