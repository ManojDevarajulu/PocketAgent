package dev.pocketagent.agent

import dev.pocketagent.fakes.FakeListener
import dev.pocketagent.fakes.FakeSpeaker
import dev.pocketagent.fakes.FakeUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VoiceUiTest {
    private val send = ConfirmRequest("Send a message", "Mom", "WhatsApp", detail = "I'm on my way")
    private val prompt = SpokenText.confirm(send)
    private val detail = SpokenText.detail(send)

    /** A card the test taps by completing [tap]; [closed] turns true when the card was cancelled (closed by code). */
    private class Card {
        val tap = CompletableDeferred<Boolean>()
        var closed = false
        suspend fun await(): Boolean = try {
            tap.await()
        } catch (e: CancellationException) {
            closed = true
            throw e
        }
    }

    private class Rig(
        answers: List<String?>,
        hangWhenDone: Boolean = false,
        listenerAvailable: Boolean = true,
        speakerAvailable: Boolean = true,
        listenMs: Long = 500,
        task: String = "reply to Mom I'm on my way",
    ) {
        val ui = FakeUi()
        val card = Card()
        val speaker = FakeSpeaker(available = speakerAvailable)
        val listener = FakeListener(answers, speaker, available = listenerAvailable, hangWhenDone = hangWhenDone, listenMs = listenMs)
        val voice = VoiceUi(ui, speaker, listener, task)

        init {
            ui.onConfirm = { card.await() }
        }
    }

    private fun TestScope.confirmWith(rig: Rig, request: ConfirmRequest = send) = async { rig.voice.confirm(request) }

    // ---- yes and no ----

    @Test fun spokenYesApproves() = runTest {
        val rig = Rig(listOf("Yes."))
        assertTrue(rig.voice.confirm(send))
        assertEquals(listOf(prompt), rig.speaker.spoken)
        assertEquals(listOf(send), rig.ui.confirmRequests)
        assertTrue(rig.card.closed, "the card is closed once the voice has answered")
        assertEquals(1, rig.listener.listens)
    }

    @Test fun spokenNoDeclines() = runTest {
        val rig = Rig(listOf("no"))
        assertFalse(rig.voice.confirm(send))
        assertEquals(listOf(prompt), rig.speaker.spoken)
        assertTrue(rig.card.closed)
    }

    // ---- the card ----

    @Test fun cardTapWinsAndStopsSpeakerAndListener() = runTest {
        // Tap Cancel while the prompt is still being spoken; the listener would have heard "yes".
        val duringPrompt = Rig(listOf("yes")).apply { speaker.hold = true }
        val first = confirmWith(duringPrompt)
        runCurrent()
        assertTrue(duringPrompt.speaker.isSaying)
        duringPrompt.card.tap.complete(false)
        runCurrent()
        assertTrue(first.isCompleted)
        assertFalse(first.await())
        assertFalse(duringPrompt.speaker.isSaying, "the prompt is cut off")
        assertEquals(1, duringPrompt.speaker.cancelledSays)
        assertTrue(duringPrompt.speaker.stops >= 1)
        assertTrue(duringPrompt.listener.stops >= 1)
        advanceUntilIdle()
        assertEquals(0, duringPrompt.listener.listens, "listening never starts after the card answered")
        assertEquals(listOf(prompt), duringPrompt.speaker.spoken)
        assertFalse(duringPrompt.card.closed, "a tapped card is not closed by code")

        // Tap Cancel while listening; the recognizer would return "yes" later.
        val duringListening = Rig(listOf("yes"), listenMs = 5_000)
        val second = confirmWith(duringListening)
        advanceTimeBy(FakeSpeaker.SAY_MS + 1_000)
        assertTrue(duringListening.listener.isListening)
        duringListening.card.tap.complete(false)
        runCurrent()
        assertTrue(second.isCompleted)
        assertFalse(second.await())
        assertFalse(duringListening.listener.isListening)
        assertEquals(1, duringListening.listener.cancelledListens)
        assertTrue(duringListening.listener.stops >= 1)
        assertTrue(duringListening.speaker.stops >= 1)
        advanceUntilIdle()
        assertEquals(1, duringListening.listener.listens)
        assertEquals(listOf(prompt), duringListening.speaker.spoken, "a later transcript changes nothing")
    }

    @Test fun cardApproveWinsOverASlowAnswer() = runTest {
        val rig = Rig(listOf("no"), listenMs = 10_000)
        val result = confirmWith(rig)
        advanceTimeBy(FakeSpeaker.SAY_MS + 2_000)
        rig.card.tap.complete(true)
        runCurrent()
        assertTrue(result.await())
        advanceUntilIdle()
        assertEquals(listOf(prompt), rig.speaker.spoken)
    }

    // ---- unclear and silence ----

    @Test fun unclearThenYesApproves() = runTest {
        val rig = Rig(listOf("maybe", "yes"))
        assertTrue(rig.voice.confirm(send))
        assertEquals(listOf(prompt, SpokenText.reAsk), rig.speaker.spoken)
    }

    @Test fun unclearTwiceDeclines() = runTest {
        val rig = Rig(listOf("maybe", "yes but wait"))
        assertFalse(rig.voice.confirm(send))
        assertEquals(listOf(prompt, SpokenText.reAsk), rig.speaker.spoken)
        assertEquals(2, rig.listener.listens)
        assertTrue(rig.card.closed)
    }

    @Test fun silenceTwiceDeclines() = runTest {
        val rig = Rig(listOf(null, null))
        assertFalse(rig.voice.confirm(send))
        assertEquals(listOf(prompt, SpokenText.reAsk), rig.speaker.spoken)
        assertEquals(2, rig.listener.listens)
    }

    // ---- no self-hearing ----

    @Test fun listeningNeverStartsBeforeThePromptEnds() = runTest {
        val rig = Rig(listOf("maybe", "yes")).apply { speaker.hold = true }
        val result = confirmWith(rig)
        advanceTimeBy(60_000)
        assertTrue(rig.speaker.isSaying)
        assertEquals(0, rig.listener.listens, "no listening while the prompt is still being spoken")
        rig.speaker.release()
        advanceTimeBy(FakeSpeaker.SAY_MS + rig.listener.listenMs + 1)
        assertEquals(1, rig.listener.listens)
        assertTrue(rig.speaker.isSaying, "the re-ask is being spoken")
        advanceTimeBy(60_000)
        assertEquals(1, rig.listener.listens, "no listening while the re-ask is still being spoken")
        rig.speaker.release()
        advanceUntilIdle()
        assertTrue(result.await())
        assertEquals(2, rig.listener.listens)
        assertTrue(rig.listener.listenStartedAfterSay)
    }

    // ---- reading the detail ----

    @Test fun readItSpeaksDetailThenYesApproves() = runTest {
        val rig = Rig(listOf("read it", "yes"))
        assertTrue(rig.voice.confirm(send))
        assertEquals(listOf(prompt, detail), rig.speaker.spoken)
        assertFalse(prompt.contains("I'm on my way"), "the prompt never speaks the detail")
        assertTrue(detail.contains("I'm on my way"))
    }

    @Test fun readDoesNotUseTheReAsk() = runTest {
        val rig = Rig(listOf("read it", "maybe", "yes"))
        assertTrue(rig.voice.confirm(send))
        assertEquals(listOf(prompt, detail, SpokenText.reAsk), rig.speaker.spoken)
    }

    @Test fun threeReadsCountAsUnclear() = runTest {
        val thenYes = Rig(listOf("read it", "read", "padho", "yes"))
        assertTrue(thenYes.voice.confirm(send))
        assertEquals(listOf(prompt, detail, detail, SpokenText.reAsk), thenYes.speaker.spoken)

        // The third read used up the re-ask, so the next unclear answer declines.
        val thenUnclear = Rig(listOf("read it", "read it", "read it", "maybe"))
        assertFalse(thenUnclear.voice.confirm(send))
        assertEquals(listOf(prompt, detail, detail, SpokenText.reAsk), thenUnclear.speaker.spoken)
        assertEquals(4, thenUnclear.listener.listens)
    }

    @Test fun readWithNoDetailSaysNoDetail() = runTest {
        val noText = send.copy(detail = null)
        val rig = Rig(listOf("read it", "no"))
        assertFalse(rig.voice.confirm(noText))
        assertEquals(2, rig.speaker.spoken.size)
        assertTrue(rig.speaker.spoken[1].startsWith(SpokenText.noDetail))
    }

    // ---- cancellation ----

    @Test fun cancellationStopsEverythingAndCloses() = runTest {
        // While listening: the loop's own timeout cancels the whole exchange.
        val listening = Rig(emptyList(), hangWhenDone = true)
        val timedOut = async { withTimeoutOrNull(120_000) { listening.voice.confirm(send) } }
        advanceTimeBy(FakeSpeaker.SAY_MS + 1_000)
        assertTrue(listening.listener.isListening)
        advanceUntilIdle()
        assertNull(timedOut.await())
        assertEquals(120_000, currentTime)
        assertTrue(listening.card.closed, "the card is closed")
        assertEquals(1, listening.listener.cancelledListens)
        assertFalse(listening.listener.isListening)
        assertTrue(listening.listener.stops >= 1)
        assertTrue(listening.speaker.stops >= 1)

        // While speaking: cancelling the job cuts the prompt off.
        val speaking = Rig(listOf("yes")).apply { speaker.hold = true }
        val job = launch { speaking.voice.confirm(send) }
        runCurrent()
        assertTrue(speaking.speaker.isSaying)
        job.cancel()
        job.join()
        assertFalse(speaking.speaker.isSaying)
        assertEquals(1, speaking.speaker.cancelledSays)
        assertTrue(speaking.card.closed)
        assertEquals(0, speaking.listener.listens)
    }

    // ---- captions ----

    @Test fun captionNeverInterruptsAPrompt() = runTest {
        val rig = Rig(listOf("yes"), listenMs = 5_000).apply { speaker.hold = true }
        val result = confirmWith(rig)
        runCurrent()
        assertTrue(rig.speaker.isSaying)
        rig.voice.onCaption("Opening WhatsApp.")
        assertEquals(emptyList(), rig.speaker.latest, "a caption never cuts into a prompt")
        rig.speaker.release()
        advanceTimeBy(1_000)
        assertTrue(rig.listener.isListening)
        rig.voice.onCaption("Tapping Send.")
        assertEquals(emptyList(), rig.speaker.latest, "nor speaks while the microphone is open")
        advanceUntilIdle()
        assertTrue(result.await())
        assertEquals(listOf("Opening WhatsApp.", "Tapping Send."), rig.ui.captions, "the screen still shows them")

        rig.voice.onCaption("Done typing.")
        assertEquals(listOf("Done typing."), rig.speaker.latest, "captions speak again once the exchange is over")
    }

    @Test fun captionsUseSayLatest() = runTest {
        val rig = Rig(emptyList())
        rig.voice.onCaption("Opening WhatsApp.")
        rig.voice.onCaption("Typing into\nMessage.")
        assertEquals(listOf("Opening WhatsApp.", "Typing into Message."), rig.speaker.latest)
        assertEquals(emptyList(), rig.speaker.spoken, "captions never use the blocking say")
        assertEquals(listOf("Opening WhatsApp.", "Typing into\nMessage."), rig.ui.captions, "the raw caption still reaches the screen")
        rig.voice.onWaitingForUser(true)
        assertEquals(listOf(true), rig.ui.waiting)
    }

    // ---- questions ----

    @Test fun askSpeaksQuestionThenReturnsTranscript() = runTest {
        val question = "Which Rahul, Rahul Sharma or Rahul K?"
        val rig = Rig(listOf(" Rahul Sharma "))
        assertEquals("Rahul Sharma", rig.voice.ask(question))
        assertEquals(listOf(SpokenText.question(question)), rig.speaker.spoken)
        assertEquals(emptyList(), rig.ui.questions, "the typed answer screen is not opened")
        assertEquals(listOf(question), rig.ui.captions, "the screen shows the question")
        assertTrue(rig.listener.listenStartedAfterSay)
        assertEquals(1, rig.listener.listens)
    }

    @Test fun askShowsTheQuestionCaptionEvenThoughTheLoopIsWaiting() = runTest {
        // AgentLoop hides the caption (waiting true) right before it asks.
        val question = "Which Rahul?"
        val rig = Rig(listOf("Rahul K"))
        rig.voice.onWaitingForUser(true)
        assertEquals("Rahul K", rig.voice.ask(question))
        val shown = rig.ui.events.indexOf("caption:$question")
        assertTrue(shown >= 0, rig.ui.events.toString())
        val lastWaiting = rig.ui.events.subList(0, shown).last { it.startsWith("waiting:") }
        assertEquals("waiting:false", lastWaiting, "the caption is visible when the question appears")
    }

    @Test fun askReturnsNullAfterTwoEmptyTries() = runTest {
        val rig = Rig(listOf(null, "   "))
        assertNull(rig.voice.ask("Which Rahul?"))
        assertEquals(2, rig.listener.listens)
        assertEquals(emptyList(), rig.ui.questions)

        val secondTry = Rig(listOf(null, "Rahul K"))
        assertEquals("Rahul K", secondTry.voice.ask("Which Rahul?"))
    }

    // ---- fallbacks ----

    @Test fun unavailableListenerFallsBackToCardAndTypedAnswer() = runTest {
        val rig = Rig(emptyList(), listenerAvailable = false)
        val result = confirmWith(rig)
        advanceTimeBy(60_000)
        assertFalse(result.isCompleted, "only the card can answer")
        assertEquals(listOf(prompt), rig.speaker.spoken, "the prompt is still spoken")
        rig.card.tap.complete(true)
        assertTrue(result.await())
        assertEquals(0, rig.listener.listens)

        rig.ui.onAsk = { "typed answer" }
        assertEquals("typed answer", rig.voice.ask("Which Rahul?"))
        assertEquals(listOf("Which Rahul?"), rig.ui.questions)
        assertEquals(0, rig.listener.listens)
    }

    @Test fun speakerLostMidExchangeLeavesTheCardToDecide() = runTest {
        // The engine dies while the prompt is spoken: the user may not have heard it, so no listening.
        val rig = Rig(listOf("yes")).apply { speaker.hold = true }
        val result = confirmWith(rig)
        runCurrent()
        assertTrue(rig.speaker.isSaying)
        rig.speaker.available = false
        rig.speaker.release()
        advanceTimeBy(60_000)
        assertFalse(result.isCompleted, "only the card can answer")
        assertEquals(0, rig.listener.listens)
        rig.card.tap.complete(true)
        assertTrue(result.await())
    }

    @Test fun listenerLostMidExchangeLeavesTheCardToDecide() = runTest {
        // The recognizer goes away after the first unclear answer, while the re-ask is spoken.
        val rig = Rig(listOf("maybe", "yes"))
        val result = confirmWith(rig)
        advanceTimeBy(FakeSpeaker.SAY_MS + rig.listener.listenMs + 1)
        assertEquals(1, rig.listener.listens)
        rig.listener.available = false
        advanceTimeBy(60_000)
        assertFalse(result.isCompleted, "an unavailable recognizer never declines on its own")
        assertEquals(1, rig.listener.listens)
        assertEquals(listOf(prompt, SpokenText.reAsk), rig.speaker.spoken)
        rig.card.tap.complete(false)
        assertFalse(result.await())
        assertFalse(rig.card.closed, "a tapped card is not closed by code")
    }

    @Test fun unavailableSpeakerStaysOnTheCard() = runTest {
        val rig = Rig(listOf("yes"), speakerAvailable = false)
        val result = confirmWith(rig)
        advanceTimeBy(60_000)
        assertFalse(result.isCompleted, "no listening without a spoken prompt")
        rig.card.tap.complete(false)
        assertFalse(result.await())
        assertEquals(0, rig.listener.listens)
        assertEquals(emptyList(), rig.speaker.spoken)
    }

    // ---- start, end and stop ----

    @Test fun endSpeaksSummaryOnlyWhenTheTaskAskedToRead() = runTest {
        val outcome = TaskOutcome(EndReason.DONE, true, "Rahul says: running late")
        val reading = Rig(emptyList(), task = "read my last message")
        reading.voice.end(outcome)
        assertEquals(listOf("Done. Rahul says: running late"), reading.speaker.latest)

        val replying = Rig(emptyList(), task = "reply to Mom hi")
        replying.voice.end(outcome)
        assertEquals(listOf("Done."), replying.speaker.latest)

        replying.voice.end(TaskOutcome(EndReason.STUCK, false, "stuck"))
        assertEquals("Couldn't finish. Check the screen.", replying.speaker.latest.last())
    }

    @Test fun startSaysOnItAndStopSilencesEverything() = runTest {
        val rig = Rig(emptyList())
        rig.voice.start()
        assertEquals(listOf(SpokenText.started), rig.speaker.latest)
        rig.voice.stop()
        assertEquals(1, rig.speaker.stops)
        assertEquals(1, rig.listener.stops)
    }
}
