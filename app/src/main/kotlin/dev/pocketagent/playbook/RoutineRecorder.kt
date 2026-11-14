package dev.pocketagent.playbook

import dev.pocketagent.safety.BlockedApps

/**
 * Turns the taps a person makes into a playbook draft. It keeps only what a playbook needs: the app, the titles of
 * the screens that opened, and the labels of the buttons that were tapped.
 *
 * What it never keeps: anything typed, anything from a password or text field, labels that look personal (an
 * email address, a long number), and anything from a blocked app or from Pocket Agent itself. The result goes
 * through the same checks as a bundled playbook, so a draft that could not ship is refused, not saved.
 */
class RoutineRecorder(private val blocked: BlockedApps, private val ownPackage: String) {
    private sealed interface Step {
        val packageName: String

        data class Tap(override val packageName: String, val label: String?) : Step
        data class Screen(override val packageName: String, val title: String) : Step
    }

    private val steps = ArrayList<Step>()

    val stepCount: Int get() = steps.size

    /** A tap on a button or row. [editable] is true for text fields and password fields, which are never recorded. */
    fun tap(packageName: String, label: String?, editable: Boolean) {
        if (editable || !wanted(packageName)) return
        add(Step.Tap(packageName, clean(label)))
    }

    /** A new screen or dialog opened, with its title when it has one. */
    fun screen(packageName: String, title: String?) {
        if (!wanted(packageName)) return
        val clean = clean(title) ?: return
        add(Step.Screen(packageName, clean))
    }

    /**
     * The draft for the app the person spent the most taps in, or a failure that says why there is none.
     * [appLabel] and [version] describe that app.
     */
    fun build(appLabel: (String) -> String, version: (String) -> String): Result<Draft> {
        val taps = steps.filterIsInstance<Step.Tap>()
        if (taps.isEmpty()) return Result.failure(IllegalStateException("No taps were recorded."))
        val packageName = taps.groupingBy { it.packageName }.eachCount().maxByOrNull { it.value }!!.key
        val mine = steps.filter { it.packageName == packageName }
        val app = clean(appLabel(packageName)) ?: packageName
        val versionName = clean(version(packageName)) ?: "unknown"
        var keep = mine.size.coerceAtMost(MAX_STEPS)
        while (keep > 0) {
            val text = render(app, packageName, versionName, mine.take(keep), cut = keep < mine.size)
            val parsed = PlaybookStore.parse("$packageName.md", text, blocked)
            if (parsed.isSuccess) return Result.success(Draft(packageName, app, text, keep, mine.size))
            // Too long is the only failure a shorter draft can fix; anything else will not improve by cutting.
            if (!parsed.exceptionOrNull()?.message.orEmpty().contains("bytes")) return Result.failure(parsed.exceptionOrNull()!!)
            keep--
        }
        return Result.failure(IllegalStateException("The recording was too long to keep."))
    }

    /** A playbook draft: the file text, which app it is for, and how many of the recorded steps fit. */
    data class Draft(val packageName: String, val app: String, val text: String, val steps: Int, val recorded: Int)

    private fun render(app: String, packageName: String, version: String, list: List<Step>, cut: Boolean): String {
        val body = StringBuilder("A routine recorded by a person doing it once. Treat it as a guide, not a script.\n\n")
        list.forEachIndexed { index, step ->
            val line = when (step) {
                is Step.Tap -> if (step.label != null) "Tap \"${step.label}\"." else "Tap a button with no label."
                is Step.Screen -> "The screen \"${step.title}\" opens."
            }
            body.append(index + 1).append(". ").append(line).append('\n')
        }
        if (cut) body.append("(The rest of the routine was left out to keep this short.)\n")
        return "---\napp: $app\npackage: $packageName\ntested_version: $version\n---\n\n$body"
    }

    private fun wanted(packageName: String): Boolean =
        packageName.isNotEmpty() && packageName != ownPackage && packageName !in IGNORED && !blocked.isBlocked(packageName, null)

    /** Skips a step that repeats the one before it: a double tap, or a screen event that fires twice. */
    private fun add(step: Step) {
        if (steps.lastOrNull() == step) return
        steps.add(step)
    }

    /** One short line with no quotes or colons, or null when there is nothing worth keeping or it could be personal. */
    private fun clean(raw: String?): String? {
        val text = raw.orEmpty().filter { !it.isISOControl() || it == ' ' }
            .replace('"', ' ').replace('\'', ' ').replace(':', ' ')
            .replace("screen_data", "").replace("app_tips", "")
            .replace(WHITESPACE, " ").trim()
        if (text.isEmpty() || text.length > MAX_LABEL) return null
        if ('@' in text || LONG_NUMBER.containsMatchIn(text.replace(" ", ""))) return null
        return text
    }

    private companion object {
        const val MAX_STEPS = 25
        const val MAX_LABEL = 40
        val WHITESPACE = Regex("\\s+")
        val LONG_NUMBER = Regex("\\d{5,}")

        /** The notification shade is not part of an app's routine. */
        val IGNORED = setOf("com.android.systemui")
    }
}
