package dev.pocketagent.ui

import android.os.Handler
import android.os.Looper
import dev.pocketagent.agent.AgentUi
import dev.pocketagent.agent.ConfirmRequest
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** The overlay implementation of [AgentUi]: confirmation card, caption and the answer screen. */
class OverlayUi(
    private val confirmCard: ConfirmCard,
    private val caption: CaptionView,
    private val openAnswerScreen: (question: String) -> Unit,
) : AgentUi {
    private val main = Handler(Looper.getMainLooper())
    private var pendingConfirm: CancellableContinuation<Boolean>? = null
    private var pendingAnswer: CompletableDeferred<String?>? = null

    override suspend fun confirm(request: ConfirmRequest): Boolean = suspendCancellableCoroutine { cont ->
        // At most one card at a time: a superseded request is answered "no" before the new card replaces it.
        onMain {
            pendingConfirm?.takeIf { it.isActive }?.resume(false)
            pendingConfirm = cont
            confirmCard.show(request) { approved ->
                onMain {
                    if (pendingConfirm === cont) pendingConfirm = null
                    if (cont.isActive) cont.resume(approved)
                }
            }
        }
        cont.invokeOnCancellation {
            onMain {
                if (pendingConfirm === cont) {
                    pendingConfirm = null
                    confirmCard.close()
                }
            }
        }
    }

    override suspend fun ask(question: String): String? {
        val answer = CompletableDeferred<String?>()
        onMain {
            pendingAnswer?.complete(null)
            pendingAnswer = answer
            openAnswerScreen(question)
        }
        try {
            return answer.await()
        } finally {
            onMain {
                if (pendingAnswer === answer) pendingAnswer = null
                // Also closes the screen when the wait was cancelled; a no-op if the user already left it.
                TaskInputActivity.closeAnswerScreen()
            }
        }
    }

    override fun onCaption(caption: String) {
        onMain { this.caption.setText(caption) }
    }

    override fun onWaitingForUser(waiting: Boolean) {
        onMain { caption.setVisible(!waiting) }
    }

    /** Called by the task screen through the session. */
    fun completeAnswer(answer: String?) {
        onMain { pendingAnswer?.complete(answer) }
    }

    /** Removes any card, clears the caption and completes a pending answer with null. */
    fun closeAll() {
        onMain {
            pendingConfirm?.takeIf { it.isActive }?.resume(false)
            pendingConfirm = null
            confirmCard.close()
            caption.setText(null)
            caption.setVisible(true)
            pendingAnswer?.complete(null)
            pendingAnswer = null
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
