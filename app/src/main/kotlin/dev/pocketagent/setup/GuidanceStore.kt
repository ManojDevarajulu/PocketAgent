package dev.pocketagent.setup

data class Guidance(val manufacturer: String, val body: String)

/**
 * The per-phone guidance files bundled with the app, validated strictly so a bad file fails the build's tests.
 * Files are named `<manufacturer>.md` (lower-case, matching `Build.MANUFACTURER`); `generic.md` is the fallback.
 */
class GuidanceStore private constructor(private val byManufacturer: Map<String, Guidance>) {

    fun forManufacturer(manufacturer: String?): Guidance {
        val key = manufacturer?.trim()?.lowercase().orEmpty()
        return byManufacturer[key] ?: byManufacturer.getValue(GENERIC)
    }

    companion object {
        const val MAX_BYTES = 2048
        private const val GENERIC = "generic"
        private val FILE_NAME = Regex("^[a-z0-9]+[.]md$")

        fun parse(fileName: String, text: String): Result<Guidance> =
            try {
                Result.success(parseOrThrow(fileName, text))
            } catch (e: IllegalArgumentException) {
                Result.failure(IllegalArgumentException("$fileName: ${e.message}"))
            }

        /** Throws [IllegalArgumentException] naming every invalid file, or a missing generic.md. */
        fun load(files: Map<String, String>): GuidanceStore {
            val results = files.map { (name, text) -> parse(name, text) }
            val problems = results.filter { it.isFailure }.map { it.exceptionOrNull()?.message.orEmpty() }.toMutableList()
            if ("$GENERIC.md" !in files) problems += "$GENERIC.md: missing, it is the fallback for every phone"
            require(problems.isEmpty()) {
                "${problems.size} guidance problem(s):\n" + problems.joinToString("\n") { "- $it" }
            }
            return GuidanceStore(results.map { it.getOrThrow() }.associateBy { it.manufacturer })
        }

        private fun parseOrThrow(fileName: String, text: String): Guidance {
            require(FILE_NAME.matches(fileName)) { "file name must match [a-z0-9]+.md" }
            val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
            val bytes = normalized.toByteArray(Charsets.UTF_8).size
            require(bytes <= MAX_BYTES) { "is $bytes bytes, the limit is $MAX_BYTES" }
            require(normalized.none { it.isISOControl() && it != '\n' && it != '\t' }) { "contains a control character" }
            val body = normalized.trim()
            require(body.isNotEmpty()) { "is blank" }
            return Guidance(fileName.removeSuffix(".md"), body)
        }
    }
}
