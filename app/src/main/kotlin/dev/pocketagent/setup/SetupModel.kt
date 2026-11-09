package dev.pocketagent.setup

import dev.pocketagent.llm.CheckResult
import dev.pocketagent.llm.ProviderConfig
import dev.pocketagent.llm.ProviderPresets
import java.net.URI
import java.net.URISyntaxException

enum class SetupStep { NOTICE, PROVIDER, MODEL_CHECK, ACCESSIBILITY, MICROPHONE, BUBBLE, TEST_TASK }

enum class MicState { NOT_ASKED, GRANTED, DENIED }

enum class StepStatus { DONE, CURRENT, TODO }

/** Everything the setup screen knows, as plain values. The Android layer gathers these; the rules live here. */
data class SetupFacts(
    val noticeAccepted: Boolean,
    val acceptedCustomHost: String?,
    val config: ProviderConfig?,
    val modelCheck: CheckResult?,
    val accessibilityOn: Boolean,
    val microphone: MicState,
    val bubbleOn: Boolean,
    val testTaskDone: Boolean,
)

/** [needsFirst] is the step to finish before an undone step can be used, or null. */
data class StepState(val step: SetupStep, val status: StepStatus, val needsFirst: SetupStep?)

data class SetupEvaluation(val steps: List<StepState>, val isReady: Boolean) {
    fun of(step: SetupStep): StepState = steps.first { it.step == step }
}

object SetupModel {
    private val presetHosts: Set<String> = ProviderPresets.all.map { hostOf(it.baseUrl) }.filter { it.isNotEmpty() }.toSet()

    fun evaluate(facts: SetupFacts): SetupEvaluation {
        val done = SetupStep.entries.associateWith { isDone(it, facts) }
        var currentTaken = false
        val steps = SetupStep.entries.map { step ->
            val status = when {
                done.getValue(step) -> StepStatus.DONE
                step == SetupStep.MICROPHONE -> StepStatus.TODO
                !currentTaken -> StepStatus.CURRENT.also { currentTaken = true }
                else -> StepStatus.TODO
            }
            StepState(step, status, if (done.getValue(step)) null else needsFirst(step, done))
        }
        val ready = SetupStep.entries.all { it == SetupStep.MICROPHONE || done.getValue(it) }
        return SetupEvaluation(steps, ready)
    }

    fun isPresetHost(host: String): Boolean = host.isNotEmpty() && host in presetHosts

    fun needsHostWarning(config: ProviderConfig): Boolean = !isPresetHost(config.host)

    private fun isDone(step: SetupStep, f: SetupFacts): Boolean = when (step) {
        SetupStep.NOTICE -> f.noticeAccepted
        SetupStep.PROVIDER -> f.config?.let {
            it.copy(model = "model").validate().ok && (!needsHostWarning(it) || f.acceptedCustomHost == it.host)
        } ?: false
        SetupStep.MODEL_CHECK -> f.modelCheck == CheckResult.Works && f.config?.model?.isNotBlank() == true
        SetupStep.ACCESSIBILITY -> f.accessibilityOn
        SetupStep.MICROPHONE -> f.microphone == MicState.GRANTED
        SetupStep.BUBBLE -> f.bubbleOn
        SetupStep.TEST_TASK -> f.testTaskDone
    }

    private fun needsFirst(step: SetupStep, done: Map<SetupStep, Boolean>): SetupStep? = when (step) {
        SetupStep.MODEL_CHECK -> SetupStep.PROVIDER.takeIf { !done.getValue(it) }
        SetupStep.TEST_TASK -> when {
            done.getValue(SetupStep.MODEL_CHECK).not() -> SetupStep.MODEL_CHECK
            done.getValue(SetupStep.ACCESSIBILITY).not() -> SetupStep.ACCESSIBILITY
            else -> null
        }
        else -> null
    }

    private fun hostOf(url: String): String = try {
        URI(url).host?.lowercase().orEmpty()
    } catch (_: URISyntaxException) {
        ""
    }
}
