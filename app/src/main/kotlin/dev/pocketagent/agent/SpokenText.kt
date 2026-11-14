package dev.pocketagent.agent

/** The exact sentences the phone speaks. Pure text rules; the speech engine lives elsewhere. */
object SpokenText {
    const val MAX = 200

    val started: String = "On it."
    val reAsk: String = "Sorry, say yes or no."
    val noDetail: String = "There's no text to read."

    fun caption(caption: String): String = clean(caption)

    fun question(question: String): String = clean(question)

    /** The action already names the target and app in most cases, so each is added only when missing. The detail is never spoken here. */
    fun confirm(request: ConfirmRequest): String {
        val parts = StringBuilder(request.action)
        if (request.target.isNotBlank() && !request.action.contains(request.target)) parts.append(", ").append(request.target)
        val needsApp = request.app.isNotBlank() && !request.action.contains(request.app)
        val joined = if (needsApp) (if (parts.contains(',')) "$parts, in ${request.app}" else "$parts in ${request.app}") else parts.toString()
        return clean("$joined? Say yes or no.")
    }

    fun detail(request: ConfirmRequest): String {
        val d = request.detail?.takeIf { it.isNotBlank() } ?: return clean("$noDetail Say yes or no.")
        return clean("It says: $d. Say yes or no.")
    }

    fun ending(outcome: TaskOutcome, includeSummary: Boolean): String {
        if (!outcome.success) return "Couldn't finish. Check the screen."
        return if (includeSummary && outcome.summary.isNotBlank()) clean("Done. ${outcome.summary}") else "Done."
    }

    /** Control characters become spaces and the text is cut at [MAX] without splitting a surrogate pair. */
    private fun clean(text: String, max: Int = MAX): String {
        val flat = text.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim()
        if (flat.length <= max) return flat
        var cut = max
        if (Character.isHighSurrogate(flat[cut - 1])) cut--
        return flat.substring(0, cut).trimEnd()
    }
}
