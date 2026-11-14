package dev.pocketagent.agent

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import java.util.concurrent.atomic.AtomicInteger

/**
 * The voice mode [AgentUi]: speaks captions, questions and confirmations through [speaker], hears answers through
 * [listener], and keeps the [wrapped] UI (card, caption) working as before. A confirmation races the spoken answer
 * against the card; only a clear "yes" or an Approve tap approves. Transcripts and spoken text are never stored.
 */
class VoiceUi(
    private val wrapped: AgentUi,
    private val speaker: Speaker,
    private val listener: Listener,
    private val task: String,
) : AgentUi {
    /** Confirmations and questions in progress. Captions stay quiet meanwhile so they never cut into a prompt or reach the open microphone. */
    private val exchanges = AtomicInteger(0)

    fun start() {
        speaker.sayLatest(SpokenText.started)
    }

    fun end(outcome: TaskOutcome) {
        speaker.sayLatest(SpokenText.ending(outcome, asksToRead(task)))
    }

    fun stop() {
        speaker.stop()
        listener.stop()
    }

    override suspend fun confirm(request: ConfirmRequest): Boolean {
        if (!speaker.available) return wrapped.confirm(request)
        return exchange {
            coroutineScope {
                val card = async { wrapped.confirm(request) }
                val voice = async { spokenAnswer(request) }
                try {
                    select {
                        card.onAwait { it }
                        voice.onAwait { it }
                    }
                } finally {
                    // The loser is cancelled: a cancelled card is closed by the wrapped UI, cancelled speech and listening stop.
                    card.cancel()
                    voice.cancel()
                }
            }
        }
    }

    /**
     * Asks until a clear yes or no. When the speaker or listener stops working mid-exchange (the prompt may not have
     * been heard, the recognizer would return nothing at once), it never returns: the card or the loop's timeout decides.
     */
    private suspend fun spokenAnswer(request: ConfirmRequest): Boolean {
        speaker.say(SpokenText.confirm(request))
        var reads = 0
        var reAsked = false
        while (true) {
            if (!listener.available || !speaker.available) awaitCancellation()
            var answer = YesNo.parse(listener.listen())
            if (answer == Answer.READ && reads >= MAX_READS) answer = Answer.UNCLEAR
            when (answer) {
                Answer.YES -> return true
                Answer.NO -> return false
                Answer.READ -> {
                    reads++
                    speaker.say(SpokenText.detail(request))
                }
                Answer.UNCLEAR -> {
                    if (reAsked) return false
                    reAsked = true
                    speaker.say(SpokenText.reAsk)
                }
            }
        }
    }

    override suspend fun ask(question: String): String? {
        if (!speaker.available || !listener.available) return wrapped.ask(question)
        // The typed answer screen would move the phone away from the app the agent works in, so the caption shows the
        // question. The loop hid the caption while it waits, so it is shown again first.
        wrapped.onWaitingForUser(false)
        wrapped.onCaption(question)
        return exchange {
            speaker.say(SpokenText.question(question))
            repeat(ASK_TRIES) {
                val heard = listener.listen()?.trim()
                if (!heard.isNullOrEmpty()) return@exchange heard
            }
            null
        }
    }

    override fun onCaption(caption: String) {
        wrapped.onCaption(caption)
        if (exchanges.get() == 0) speaker.sayLatest(SpokenText.caption(caption))
    }

    override fun onWaitingForUser(waiting: Boolean) {
        wrapped.onWaitingForUser(waiting)
    }

    /** Runs a spoken exchange; however it ends (answer, card, cancellation), speech and listening stop. */
    private suspend fun <T> exchange(block: suspend () -> T): T {
        exchanges.incrementAndGet()
        try {
            return block()
        } finally {
            speaker.stop()
            listener.stop()
            exchanges.decrementAndGet()
        }
    }

    private companion object {
        const val MAX_READS = 2
        const val ASK_TRIES = 2
    }
}
