package dev.pocketagent.device

/** One safe line: no wrapper tags, no quotes, no line breaks, at most [max] characters. */
internal fun cleanUntrusted(raw: String, max: Int): String {
    val sb = StringBuilder(raw.length)
    for (c in raw) {
        sb.append(
            when {
                c == '<' -> '‹'
                c == '>' -> '›'
                c == '"' -> '\''
                c.isISOControl() || c == ' ' || c == ' ' -> ' '
                else -> c
            },
        )
    }
    val text = sb.toString().trim()
    if (text.length <= max) return text
    var cut = max - 1
    if (Character.isHighSurrogate(text[cut - 1])) cut--
    return text.substring(0, cut) + "…"
}
