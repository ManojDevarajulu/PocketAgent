package dev.pocketagent.agent

import dev.pocketagent.device.ScreenSerializer
import dev.pocketagent.device.node
import dev.pocketagent.device.screenOf
import dev.pocketagent.fakes.FakeDevice
import dev.pocketagent.fakes.FakeLlm
import dev.pocketagent.fakes.FakeUi
import dev.pocketagent.llm.LlmClient
import dev.pocketagent.llm.LlmResult
import dev.pocketagent.llm.LlmSession
import dev.pocketagent.llm.Turn
import dev.pocketagent.playbook.PlaybookStore
import dev.pocketagent.safety.BlockedApps
import dev.pocketagent.safety.SafetyGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AgentRunnerTest {
    private val own = "dev.pocketagent"
    private val payScreen = screenOf(node("a1", role = "button", text = "Pay now", clickable = true))

    private fun call(name: String, vararg args: Pair<String, Any>) = LlmResult.Call(
        "id-$name",
        name,
        JsonObject(args.associate { (k, v) -> k to if (v is Boolean) JsonPrimitive(v) else JsonPrimitive(v.toString()) }),
    )

    private val done = call("done", "summary" to "all done", "success" to true)
    private val tapPay = call("tap", "node_id" to "a1")

    /** A model that never answers, and notes when its call was cancelled. */
    private class HangingLlm : LlmClient {
        var cancelled = false
        override val supportsImages = true
        override fun newSession(systemPrompt: String, tools: List<ToolDefinition>) = object : LlmSession {
            override suspend fun send(turn: Turn): LlmResult {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        }
    }

    private class Rig(scope: TestScope, llm: LlmClient, val device: FakeDevice, val ui: FakeUi) {
        val loopUi = mutableListOf<AgentUi>()
        val runner = AgentRunner(
            scope.backgroundScope,
            { wrapped ->
                loopUi += wrapped
                AgentLoop(llm, device, SafetyGate(BlockedApps(), "dev.pocketagent"), PlaybookStore.load(emptyMap(), BlockedApps()), ScreenSerializer("dev.pocketagent"), wrapped)
            },
            ui,
        )
    }

    private fun TestScope.rig(llm: LlmClient, ui: FakeUi = FakeUi()) = Rig(this, llm, FakeDevice(listOf(payScreen)), ui)

    @Test fun secondTaskIsRefusedWhileRunning() = runTest {
        val llm = HangingLlm()
        val r = rig(llm)
        assertTrue(r.runner.start("one", payScreen))
        assertFalse(r.runner.start("two", payScreen))
        runCurrent()
        assertEquals(AgentState.Running, r.runner.state.value)
        assertFalse(llm.cancelled)
        r.runner.cancel()
    }

    @Test fun startingSetsRunningImmediately() = runTest {
        val r = rig(HangingLlm())
        assertEquals(AgentState.Idle, r.runner.state.value)
        r.runner.start("one", payScreen)
        assertEquals(AgentState.Running, r.runner.state.value)
        r.runner.cancel()
    }

    @Test fun cancelDuringModelCallStopsWithinOneSecond() = runTest {
        val llm = HangingLlm()
        val r = rig(llm)
        r.runner.start("one", payScreen)
        runCurrent()
        r.runner.cancel()
        advanceTimeBy(1_000)
        assertEquals(AgentState.Stopped, r.runner.state.value)
        assertTrue(llm.cancelled, "the model call must really be cancelled, not just hidden")
    }

    @Test fun cancelWhileConfirmationOpenNeverPerformsAction() = runTest {
        val ui = FakeUi().apply { onConfirm = { awaitCancellation() } }
        val r = rig(FakeLlm(listOf(tapPay, done)), ui)
        r.runner.start("pay", payScreen)
        runCurrent()
        assertEquals(1, ui.confirmRequests.size)
        r.runner.cancel()
        runCurrent()
        assertTrue(r.device.actions.isEmpty())
        assertEquals(AgentState.Stopped, r.runner.state.value)
        assertEquals(listOf(true, false), ui.waiting)
    }

    @Test fun finishedStateCarriesOutcome() = runTest {
        val r = rig(FakeLlm(listOf(done)))
        r.runner.start("t", payScreen)
        runCurrent()
        assertEquals(AgentState.Finished(TaskOutcome(EndReason.DONE, true, "all done")), r.runner.state.value)
    }

    @Test fun waitingStateDuringConfirmation() = runTest {
        val answer = CompletableDeferred<Boolean>()
        val ui = FakeUi().apply { onConfirm = { answer.await() } }
        val r = rig(FakeLlm(listOf(tapPay, done)), ui)
        r.runner.start("pay", payScreen)
        runCurrent()
        assertEquals(AgentState.WaitingForUser, r.runner.state.value)
        answer.complete(false)
        runCurrent()
        assertIs<AgentState.Finished>(r.runner.state.value)
        assertEquals(listOf(true, false), ui.waiting, "the real UI is told too")
    }

    @Test fun anotherTaskCanStartAfterFinishedOrStopped() = runTest {
        val r = rig(FakeLlm(listOf(done, done)))
        r.runner.start("one", payScreen)
        runCurrent()
        assertTrue(r.runner.start("two", payScreen))
        runCurrent()
        assertIs<AgentState.Finished>(r.runner.state.value)

        val h = rig(HangingLlm())
        h.runner.start("one", payScreen)
        h.runner.cancel()
        assertTrue(h.runner.start("two", payScreen))
        runCurrent()
        assertEquals(AgentState.Running, h.runner.state.value, "the cancelled first run must not overwrite the second")
        h.runner.cancel()
    }

    @Test fun cancelWhenIdleChangesNothing() = runTest {
        val r = rig(HangingLlm())
        r.runner.cancel()
        assertEquals(AgentState.Idle, r.runner.state.value)
    }

    @Test fun cancelAfterFinishedKeepsTheOutcome() = runTest {
        val r = rig(FakeLlm(listOf(done)))
        r.runner.start("t", payScreen)
        runCurrent()
        r.runner.cancel()
        assertIs<AgentState.Finished>(r.runner.state.value)
    }

    @Test fun aCrashInsideTheLoopEndsTheTaskInsteadOfHangingAsRunning() = runTest {
        val broken = object : LlmClient {
            override val supportsImages = true
            override fun newSession(systemPrompt: String, tools: List<ToolDefinition>): LlmSession = error("boom")
        }
        val r = rig(broken)
        r.runner.start("t", payScreen)
        runCurrent()
        val state = r.runner.state.value
        assertIs<AgentState.Finished>(state)
        assertFalse(state.outcome.success)
        assertFalse(state.outcome.summary.contains("boom"), "internal error text must not reach the UI")
        assertTrue(r.runner.start("again", payScreen))
        r.runner.cancel()
    }

    @Test fun aStackOverflowInsideTheLoopAlsoEndsTheTask() = runTest {
        val deep = object : LlmClient {
            override val supportsImages = true
            override fun newSession(systemPrompt: String, tools: List<ToolDefinition>): LlmSession = throw StackOverflowError()
        }
        val r = rig(deep)
        r.runner.start("t", payScreen)
        runCurrent()
        assertIs<AgentState.Finished>(r.runner.state.value)
    }

    @Test fun captionsAndQuestionsReachTheRealUi() = runTest {
        val ui = FakeUi().apply { onAsk = { "blue" } }
        val r = rig(FakeLlm(listOf(call("ask_user", "question" to "Colour?"), tapPay, done)), ui)
        r.runner.start("t", payScreen)
        runCurrent()
        assertEquals(listOf("Colour?"), ui.questions)
        assertEquals(listOf("Tapping Pay now"), ui.captions)
    }
}
