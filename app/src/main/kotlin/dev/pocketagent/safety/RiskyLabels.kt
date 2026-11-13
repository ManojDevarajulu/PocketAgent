package dev.pocketagent.safety

import java.text.Normalizer
import java.util.regex.Pattern

/**
 * The form of a label that matching works on: compatibility-normalised (NFKC, so fullwidth letters become plain
 * ones) with invisible format characters removed (zero-width spaces and joiners, soft hyphens, bidirectional
 * marks, byte-order marks). Without this, "Pa" + a zero-width space + "y" would not look like "Pay" to the
 * rules, though a person reads it as "Pay". Look-alike letters from other scripts are still not caught.
 */
internal fun matchForm(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC).filter { Character.getType(it) != Character.FORMAT.toInt() }

/** Finds any of the terms at the start of a word, ignoring case, after [matchForm]. Letters and digits both count as word characters. */
internal class WordStartMatcher(terms: Collection<String>) {
    private val pattern: Pattern = Pattern.compile(
        "(?<![\\p{L}\\p{N}])(?:" + terms.joinToString("|") { Pattern.quote(matchForm(it)) } + ")",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
    )

    fun find(text: String): Boolean = pattern.matcher(matchForm(text)).find()
}

internal fun wordStartPattern(terms: Collection<String>) = WordStartMatcher(terms)

/**
 * Buttons that spend money, send something, or destroy something. Matching is by word start, so a term also
 * matches longer words ("postal" matches "post"). That over-matching is accepted: the cost is one extra confirmation.
 */
object RiskyLabels {
    private val terms = listOf(
        "pay", "place order", "buy", "send", "post", "delete", "remove", "confirm", "transfer", "uninstall", "install",
        // A call costs money and reaches a real person, so the Call button needs approval like Send.
        "call", "dial",
        "भुगतान", "भेजें", "खरीदें", "हटाएं", "पुष्टि", "ऑर्डर करें",
    )
    private val pattern = wordStartPattern(terms)

    fun isRisky(label: String?): Boolean = !label.isNullOrBlank() && pattern.find(label)
}
