package dev.pocketagent.safety

import dev.pocketagent.device.DeviceAction
import dev.pocketagent.device.Screen
import dev.pocketagent.device.ScreenNode
import java.util.Locale

sealed interface Verdict {
    data object Allow : Verdict
    data class Confirm(val reason: String) : Verdict
    data class Block(val reason: String) : Verdict
}

/**
 * Decides whether an action may run, needs the user's confirmation, or is refused. Plain rules only: the
 * model proposes, and nothing it writes can change a verdict. The model can raise the risk of an action
 * (the irreversible flag) but never lower it.
 */
class SafetyGate(private val blocked: BlockedApps, private val ownPackage: String) {

    /** Blocks the whole task when the foreground app is on the block list. */
    fun checkApp(screen: Screen): Verdict =
        if (blocked.isBlocked(screen.packageName, screen.appLabel)) {
            Verdict.Block("${screen.appLabel ?: screen.packageName} is on the block list")
        } else {
            Verdict.Allow
        }

    fun check(action: DeviceAction, screen: Screen, irreversible: Boolean): Verdict = when (action) {
        is DeviceAction.Tap -> checkTap(action.node, screen, irreversible)
        is DeviceAction.TapAt -> checkTapAt(action.x, action.y, screen, irreversible)
        is DeviceAction.TypeText -> when {
            action.node.packageName == ownPackage -> OWN_WINDOW
            action.node.isPassword -> Verdict.Block("password fields are never typed into")
            else -> Verdict.Allow
        }
        is DeviceAction.Scroll -> if (action.node?.packageName == ownPackage) OWN_WINDOW else Verdict.Allow
        is DeviceAction.OpenApp ->
            if (blocked.isBlocked("", action.name)) Verdict.Block("${action.name} is on the block list") else Verdict.Allow
        DeviceAction.Back, DeviceAction.Home -> Verdict.Allow
    }

    private fun checkTapAt(x: Int, y: Int, screen: Screen, irreversible: Boolean): Verdict {
        if (screen.ownWindowBounds.any { it.contains(x, y) }) return OWN_WINDOW
        if (screen.all().none { it.label() != null }) {
            return Verdict.Confirm("this screen has no readable labels, so the tap cannot be checked")
        }
        // Judge every element under the point, not only one of them: overlapping windows decide which element
        // really receives the touch, and a screen's tree order does not tell us that.
        screen.all().filter { it.bounds.contains(x, y) }.firstNotNullOfOrNull { riskyOnPath(it, screen) }
            ?.let { return Verdict.Confirm("\"$it\" looks like it pays, sends or deletes") }
        val target = screen.deepestAt(x, y)
        if (target != null) {
            val verdict = checkTap(target, screen, irreversible)
            val blindTap = target.label() == null && screen.all().any { RiskyLabels.isRisky(it.label()) }
            return if (verdict == Verdict.Allow && blindTap) {
                Verdict.Confirm("an unlabelled element was tapped while a risky button is visible")
            } else {
                verdict
            }
        }
        return when {
            irreversible -> Verdict.Confirm("the agent marked this action as irreversible")
            screen.all().any { RiskyLabels.isRisky(it.label()) } -> Verdict.Confirm("a risky button is visible and the tap is not on a known element")
            else -> Verdict.Allow
        }
    }

    private fun checkTap(node: ScreenNode, screen: Screen, irreversible: Boolean): Verdict {
        if (node.packageName == ownPackage) return OWN_WINDOW
        if (irreversible) return Verdict.Confirm("the agent marked this action as irreversible")
        riskyLabelNear(node, screen)?.let { return Verdict.Confirm("\"$it\" looks like it pays, sends or deletes") }
        if ((sequenceOf(node) + descendants(node)).any { isGenericAffirmative(it) } && screen.showsMoney()) {
            return Verdict.Confirm("a generic confirmation button on a screen that shows an amount")
        }
        return Verdict.Allow
    }

    /** The risky label on the node, any descendant, or the nearest labelled ancestor, or null if none is risky. */
    private fun riskyLabelNear(node: ScreenNode, screen: Screen): String? {
        val inside = sequenceOf(node) + descendants(node)
        inside.mapNotNull { it.label() }.firstOrNull { RiskyLabels.isRisky(it) }?.let { return it }
        var ancestor = screen.parentOf(node)
        while (ancestor != null) {
            val label = ancestor.label()
            if (label != null) return label.takeIf { RiskyLabels.isRisky(it) }
            ancestor = screen.parentOf(ancestor)
        }
        return null
    }

    /** The risky label on the node itself or on its nearest labelled ancestor, or null. */
    private fun riskyOnPath(node: ScreenNode, screen: Screen): String? {
        node.label()?.takeIf { RiskyLabels.isRisky(it) }?.let { return it }
        var ancestor = screen.parentOf(node)
        while (ancestor != null) {
            val label = ancestor.label()
            if (label != null) return label.takeIf { RiskyLabels.isRisky(it) }
            ancestor = screen.parentOf(ancestor)
        }
        return null
    }

    private fun descendants(node: ScreenNode): Sequence<ScreenNode> =
        node.children.asSequence().flatMap { sequenceOf(it) + descendants(it) }

    private fun isGenericAffirmative(node: ScreenNode): Boolean =
        node.label()?.lowercase(Locale.ROOT) in AFFIRMATIVES

    private fun Screen.showsMoney(): Boolean = all().mapNotNull { it.label() }.any { label ->
        label.any { it in CURRENCY_SYMBOLS } || MONEY_WORDS.matcher(label).find()
    }

    private companion object {
        val OWN_WINDOW = Verdict.Block("Pocket Agent's own windows are off limits")
        val AFFIRMATIVES = setOf("ok", "yes", "continue", "proceed", "done", "submit")
        const val CURRENCY_SYMBOLS = "₹\$€£"
        val MONEY_WORDS = wordStartPattern(listOf("total", "order", "pay", "amount"))
    }
}
