package dev.pocketagent.safety

import java.util.regex.Pattern

/** A pattern matching any of [terms] at the start of a word, ignoring case. Letters and digits both count as word characters. */
internal fun wordStartPattern(terms: Collection<String>): Pattern =
    Pattern.compile(
        "(?<![\\p{L}\\p{N}])(?:" + terms.joinToString("|") { Pattern.quote(it) } + ")",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.UNICODE_CHARACTER_CLASS,
    )

/**
 * Buttons that spend money, send something, or destroy something. Matching is by word start, so a term also
 * matches longer words ("postal" matches "post"). That over-matching is accepted: the cost is one extra confirmation.
 */
object RiskyLabels {
    private val terms = listOf(
        "pay", "place order", "buy", "send", "post", "delete", "remove", "confirm", "transfer", "uninstall", "install",
        "भुगतान", "भेजें", "खरीदें", "हटाएं", "पुष्टि", "ऑर्डर करें",
    )
    private val pattern = wordStartPattern(terms)

    fun isRisky(label: String?): Boolean = !label.isNullOrBlank() && pattern.matcher(label).find()
}
