package dev.pocketagent.ui

import android.app.Activity
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import dev.pocketagent.R

/** What the task screen needs from the session. The session sets [current] while the service is on. */
interface TaskScreenHost {
    fun startTask(text: String)
    fun dropSavedScreen()
    fun completeAnswer(answer: String?)

    companion object {
        @Volatile
        var current: TaskScreenHost? = null
    }
}

/** Asks for a task, or, when [EXTRA_QUESTION] is present, answers a question from the agent. */
class TaskInputActivity : Activity() {
    private var question: String? = null
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        question = intent.getStringExtra(EXTRA_QUESTION)
        setFinishOnTouchOutside(true)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        if (question != null) answerInstance = this

        val pad = Overlays.dp(this, 20)
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(MAX_TEXT))
            setHint(if (question != null) R.string.answer_hint else R.string.task_hint)
            setTextColor(getColor(R.color.card_text))
            setHintTextColor(getColor(R.color.card_muted))
            minLines = 2
            maxLines = 6
            gravity = Gravity.TOP or Gravity.START
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(getColor(R.color.card_background))
        }
        question?.let {
            root.addView(TextView(this).apply {
                text = it
                textSize = 18f
                setTextColor(getColor(R.color.card_text))
                setPadding(0, 0, 0, pad / 2)
            })
        }
        root.addView(input)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, pad / 2, 0, 0)
        }
        val cancel = Button(this).apply {
            setText(if (question != null) R.string.answer_skip else R.string.task_cancel)
            setOnClickListener { dismiss() }
        }
        val go = Button(this).apply {
            setText(if (question != null) R.string.answer_submit else R.string.task_go)
            setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isEmpty()) {
                    input.error = getString(R.string.task_empty)
                    return@setOnClickListener
                }
                handled = true
                if (question != null) {
                    TaskScreenHost.current?.completeAnswer(text)
                } else {
                    TaskScreenHost.current?.startTask(text)
                }
                finish()
            }
        }
        buttons.addView(cancel)
        buttons.addView(go)
        root.addView(buttons)
        setContentView(root)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.TOP)
    }

    @Deprecated("Back is treated as a dismissal.")
    override fun onBackPressed() {
        dismiss()
    }

    override fun onStop() {
        // Home or an app switch only stops this task-less screen and the user cannot return to it, so it counts as a dismissal.
        if (!isChangingConfigurations) dismiss()
        super.onStop()
    }

    override fun onDestroy() {
        // An outside tap or a system close ends up here without going through a button.
        if (isFinishing) dismiss(finishNow = false)
        if (answerInstance === this) answerInstance = null
        super.onDestroy()
    }

    private fun dismiss(finishNow: Boolean = true) {
        if (!handled) {
            handled = true
            if (question != null) {
                TaskScreenHost.current?.completeAnswer(null)
            } else {
                TaskScreenHost.current?.dropSavedScreen()
            }
        }
        if (finishNow) finish()
    }

    companion object {
        const val EXTRA_QUESTION = "question"
        const val MAX_TEXT = 2_000

        @Volatile
        private var answerInstance: TaskInputActivity? = null

        /** Closes the answer screen without reporting a dismissal (the session already has its answer or gave up). */
        fun closeAnswerScreen() {
            answerInstance?.let {
                it.handled = true
                it.finish()
            }
        }
    }
}
