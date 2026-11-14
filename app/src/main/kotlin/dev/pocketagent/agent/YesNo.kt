package dev.pocketagent.agent

enum class Answer { YES, NO, READ, UNCLEAR }

/** Turns a spoken answer into yes, no or read. The whole utterance must match an entry, so "yes send it to everyone" is unclear. */
object YesNo {
    private val yes = setOf("yes", "yeah", "yep", "yup", "sure", "ok", "okay", "confirm", "do it", "go ahead", "send it", "haan", "haa", "ha", "हाँ", "हां", "जी हाँ")
    private val no = setOf("no", "nope", "cancel", "stop", "don't", "do not", "wait", "nahi", "nahin", "नहीं", "ना")
    private val read = setOf("read it", "read", "what does it say", "padho", "पढ़ो")

    fun parse(transcript: String?): Answer {
        val text = normalise(transcript ?: return Answer.UNCLEAR)
        return when (text) {
            in yes -> Answer.YES
            in no -> Answer.NO
            in read -> Answer.READ
            else -> Answer.UNCLEAR
        }
    }

    private fun normalise(raw: String): String =
        raw.lowercase().replace('’', '\'')
            .filterNot { it in ".,!?;:\"" || it == '।' }
            .trim('\'', ' ')
            .replace(Regex("\\s+"), " ")
}
