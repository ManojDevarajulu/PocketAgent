package dev.pocketagent.playbook

import dev.pocketagent.safety.BlockedApps

/**
 * The playbooks bundled with the app, validated strictly. A playbook becomes trusted guidance for the model,
 * so a file that is oversized, malformed, aimed at a blocked app, or trying to confuse the screen-data wrapper
 * is rejected, and the build's tests fail until it is fixed.
 */
class PlaybookStore private constructor(private val byPackage: Map<String, Playbook>) {

    fun forPackage(packageName: String): Playbook? = byPackage[packageName]

    companion object {
        const val MAX_BYTES = 2048
        private val KEYS = listOf("app", "package", "tested_version")
        private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

        fun parse(fileName: String, text: String, blocked: BlockedApps): Result<Playbook> =
            try {
                Result.success(parseOrThrow(fileName, text, blocked))
            } catch (e: IllegalArgumentException) {
                Result.failure(IllegalArgumentException("$fileName: ${e.message}"))
            }

        /** Throws [IllegalArgumentException] naming every invalid file, so one run shows all the problems. */
        fun load(files: Map<String, String>, blocked: BlockedApps): PlaybookStore {
            val results = files.map { (name, text) -> parse(name, text, blocked) }
            val failures = results.filter { it.isFailure }
            require(failures.isEmpty()) {
                "${failures.size} invalid playbook(s):\n" + failures.joinToString("\n") { "- ${it.exceptionOrNull()?.message}" }
            }
            return PlaybookStore(results.map { it.getOrThrow() }.associateBy { it.packageName })
        }

        private fun parseOrThrow(fileName: String, text: String, blocked: BlockedApps): Playbook {
            val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
            val bytes = normalized.toByteArray(Charsets.UTF_8).size
            require(bytes <= MAX_BYTES) { "is $bytes bytes, the limit is $MAX_BYTES" }
            require(normalized.none { it.isISOControl() && it != '\n' && it != '\t' }) { "contains a control character" }
            require("screen_data" !in normalized) { "must not mention the screen_data wrapper" }

            val lines = normalized.split('\n')
            require(lines.first().trim() == "---") { "must start with a --- line" }
            val close = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
            requireNotNull(close) { "frontmatter is not closed by a --- line" }

            val fields = HashMap<String, String>()
            for (line in lines.subList(1, close)) {
                if (line.isBlank()) continue
                val colon = line.indexOf(':')
                require(colon > 0) { "frontmatter line \"${line.trim()}\" is not key: value" }
                val key = line.substring(0, colon).trim()
                require(key in KEYS) { "unknown frontmatter key \"$key\"" }
                require(key !in fields) { "duplicate key \"$key\"" }
                fields[key] = unquote(line.substring(colon + 1).trim())
            }
            for (key in KEYS) require(!fields[key].isNullOrEmpty()) { "missing $key" }

            val app = fields.getValue("app")
            val packageName = fields.getValue("package")
            require(PACKAGE_NAME.matches(packageName)) { "\"$packageName\" is not a valid package name" }
            require(fileName == "$packageName.md") { "file name must be $packageName.md" }
            require(!blocked.isBlocked(packageName, app)) { "$app ($packageName) is on the block list" }

            val body = lines.drop(close + 1).joinToString("\n").trim()
            require(body.isNotEmpty()) { "has no body after the frontmatter" }
            return Playbook(app, packageName, fields.getValue("tested_version"), body)
        }

        private fun unquote(value: String): String =
            if (value.length >= 2 && (value.first() == '"' || value.first() == '\'') && value.last() == value.first()) {
                value.substring(1, value.length - 1).trim()
            } else {
                value
            }
    }
}
