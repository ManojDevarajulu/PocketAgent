package dev.pocketagent.setup

import dev.pocketagent.llm.CheckResult
import dev.pocketagent.llm.ProviderConfig
import dev.pocketagent.llm.ProviderKind
import dev.pocketagent.llm.ProviderPresets
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupModelTest {
    private fun config(baseUrl: String = "https://api.anthropic.com", model: String = "some-model") =
        ProviderConfig(ProviderKind.ANTHROPIC, baseUrl, "sk-test", model, false)

    private fun fresh() = SetupFacts(
        noticeAccepted = false, acceptedCustomHost = null, config = null, modelCheck = null,
        accessibilityOn = false, microphone = MicState.NOT_ASKED, bubbleOn = false, testTaskDone = false,
    )

    private fun ready() = fresh().copy(
        noticeAccepted = true, config = config(), modelCheck = CheckResult.Works,
        accessibilityOn = true, bubbleOn = true, testTaskDone = true,
    )

    private fun current(facts: SetupFacts) =
        SetupModel.evaluate(facts).steps.firstOrNull { it.status == StepStatus.CURRENT }?.step

    @Test
    fun freshInstallStartsAtTheNotice() {
        val e = SetupModel.evaluate(fresh())
        assertEquals(SetupStep.NOTICE, current(fresh()))
        assertEquals(StepStatus.CURRENT, e.of(SetupStep.NOTICE).status)
        listOf(SetupStep.PROVIDER, SetupStep.MODEL_CHECK, SetupStep.ACCESSIBILITY, SetupStep.BUBBLE, SetupStep.TEST_TASK)
            .forEach { assertEquals(StepStatus.TODO, e.of(it).status, it.name) }
        assertEquals(StepStatus.TODO, e.of(SetupStep.MICROPHONE).status)
        assertFalse(e.isReady)
        assertEquals(SetupStep.entries, e.steps.map { it.step })
    }

    @Test
    fun eachStepBecomesDoneInOrder() {
        var f = fresh()
        val expected = listOf(
            SetupStep.NOTICE to { x: SetupFacts -> x.copy(noticeAccepted = true) },
            SetupStep.PROVIDER to { x: SetupFacts -> x.copy(config = config()) },
            SetupStep.MODEL_CHECK to { x: SetupFacts -> x.copy(modelCheck = CheckResult.Works) },
            SetupStep.ACCESSIBILITY to { x: SetupFacts -> x.copy(accessibilityOn = true) },
            SetupStep.BUBBLE to { x: SetupFacts -> x.copy(bubbleOn = true) },
            SetupStep.TEST_TASK to { x: SetupFacts -> x.copy(testTaskDone = true) },
        )
        for ((step, apply) in expected) {
            assertEquals(step, current(f))
            f = apply(f)
            assertEquals(StepStatus.DONE, SetupModel.evaluate(f).of(step).status)
        }
        assertTrue(SetupModel.evaluate(f).isReady)
        assertNull(current(f))
    }

    @Test
    fun microphoneNeverBlocksReadiness() {
        val denied = SetupModel.evaluate(ready().copy(microphone = MicState.DENIED))
        assertTrue(denied.isReady)
        assertEquals(StepStatus.TODO, denied.of(SetupStep.MICROPHONE).status)
        val granted = SetupModel.evaluate(ready().copy(microphone = MicState.GRANTED))
        assertTrue(granted.isReady)
        assertEquals(StepStatus.DONE, granted.of(SetupStep.MICROPHONE).status)
        assertEquals(StepStatus.TODO, SetupModel.evaluate(fresh().copy(microphone = MicState.NOT_ASKED)).of(SetupStep.MICROPHONE).status)
    }

    @Test
    fun anEditedConfigWithNoCheckReopensTheModelStep() {
        val f = ready().copy(modelCheck = null, accessibilityOn = false, bubbleOn = false, testTaskDone = false)
        assertEquals(SetupStep.MODEL_CHECK, current(f))
    }

    @Test
    fun aFailedCheckIsNotDone() {
        listOf(CheckResult.NoToolCalls, CheckResult.BadKey, CheckResult.Unreachable, CheckResult.Other("x")).forEach {
            val e = SetupModel.evaluate(ready().copy(modelCheck = it))
            assertEquals(StepStatus.CURRENT, e.of(SetupStep.MODEL_CHECK).status, it.toString())
            assertFalse(e.isReady)
        }
    }

    @Test
    fun blankModelNeverPassesTheCheck() {
        val f = ready().copy(config = config(model = ""))
        val e = SetupModel.evaluate(f)
        assertEquals(StepStatus.DONE, e.of(SetupStep.PROVIDER).status)
        assertEquals(StepStatus.CURRENT, e.of(SetupStep.MODEL_CHECK).status)
        assertFalse(e.isReady)
    }

    @Test
    fun aPresetHostNeedsNoWarning() {
        ProviderPresets.all.forEach { preset ->
            val c = ProviderPresets.configFor(preset, "sk-test", "m")
            assertTrue(SetupModel.isPresetHost(c.host), preset.id)
            assertFalse(SetupModel.needsHostWarning(c), preset.id)
            val e = SetupModel.evaluate(ready().copy(config = c))
            assertEquals(StepStatus.DONE, e.of(SetupStep.PROVIDER).status, preset.id)
        }
    }

    @Test
    fun aCustomHostNeedsAnAcceptedWarning() {
        val c = config("https://llm.example.com/v1")
        assertTrue(SetupModel.needsHostWarning(c))
        assertFalse(SetupModel.isPresetHost(c.host))
        val f = ready().copy(config = c, acceptedCustomHost = null)
        assertEquals(SetupStep.PROVIDER, current(f))
        val accepted = SetupModel.evaluate(f.copy(acceptedCustomHost = "llm.example.com"))
        assertEquals(StepStatus.DONE, accepted.of(SetupStep.PROVIDER).status)
        assertTrue(accepted.isReady)
    }

    @Test
    fun aChangedCustomHostAsksAgain() {
        val f = ready().copy(config = config("https://b.example.com/v1"), acceptedCustomHost = "a.example.com")
        assertEquals(SetupStep.PROVIDER, current(f))
    }

    @Test
    fun accessibilitySwitchedOffAfterSetupReopensIt() {
        val e = SetupModel.evaluate(ready().copy(accessibilityOn = false))
        assertEquals(SetupStep.ACCESSIBILITY, current(ready().copy(accessibilityOn = false)))
        assertFalse(e.isReady)
    }

    @Test
    fun testTaskNeedsTheModelCheckFirst() {
        val e = SetupModel.evaluate(fresh())
        assertEquals(SetupStep.MODEL_CHECK, e.of(SetupStep.TEST_TASK).needsFirst)
        assertEquals(SetupStep.PROVIDER, e.of(SetupStep.MODEL_CHECK).needsFirst)
        assertNull(e.of(SetupStep.PROVIDER).needsFirst)
        assertNull(e.of(SetupStep.BUBBLE).needsFirst)
    }

    @Test
    fun needsAccessibilityWhenOnlyThatIsMissing() {
        val e = SetupModel.evaluate(ready().copy(accessibilityOn = false, testTaskDone = false))
        assertEquals(SetupStep.ACCESSIBILITY, e.of(SetupStep.TEST_TASK).needsFirst)
        val done = SetupModel.evaluate(ready().copy(testTaskDone = false))
        assertNull(done.of(SetupStep.TEST_TASK).needsFirst)
    }

    @Test
    fun modelCheckNeedsNothingOnceProviderIsDone() {
        val e = SetupModel.evaluate(fresh().copy(noticeAccepted = true, config = config(), modelCheck = null))
        assertEquals(StepStatus.CURRENT, e.of(SetupStep.MODEL_CHECK).status)
        assertNull(e.of(SetupStep.MODEL_CHECK).needsFirst)
    }

    @Test
    fun modelCheckNeedsTheProviderWhenThatIsMissing() {
        val e = SetupModel.evaluate(fresh().copy(noticeAccepted = true, config = null))
        assertEquals(SetupStep.PROVIDER, e.of(SetupStep.MODEL_CHECK).needsFirst)
    }

    @Test
    fun testTaskNeedsTheModelCheckBeforeAccessibility() {
        val e = SetupModel.evaluate(ready().copy(modelCheck = null, accessibilityOn = false, testTaskDone = false))
        assertEquals(SetupStep.MODEL_CHECK, e.of(SetupStep.TEST_TASK).needsFirst)
    }

    @Test
    fun aLookalikeHostIsNotAPresetHost() {
        val c = config("https://openrouter.ai.evil.com/v1")
        assertFalse(SetupModel.isPresetHost(c.host))
        assertTrue(SetupModel.needsHostWarning(c))
    }

    @Test
    fun presetHostMatchingIgnoresCasePortAndTrailingSlash() {
        listOf(
            "https://OpenRouter.AI/api/v1", "https://openrouter.ai:443/api/v1", "https://openrouter.ai/api/v1/",
        ).forEach { assertFalse(SetupModel.needsHostWarning(config(it)), it) }
    }

    @Test
    fun anUnparseableUrlIsNeverAPresetHost() {
        assertFalse(SetupModel.isPresetHost(""))
        assertTrue(SetupModel.needsHostWarning(config("not a url")))
    }
}
