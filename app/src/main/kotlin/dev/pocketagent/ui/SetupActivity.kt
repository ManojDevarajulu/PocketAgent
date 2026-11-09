package dev.pocketagent.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import dev.pocketagent.R
import dev.pocketagent.android.PocketAccessibilityService
import dev.pocketagent.android.SetupStores
import dev.pocketagent.llm.CheckResult
import dev.pocketagent.llm.HttpCaller
import dev.pocketagent.llm.OkHttpTransport
import dev.pocketagent.llm.ProviderCheck
import dev.pocketagent.llm.ProviderConfig
import dev.pocketagent.llm.ProviderKind
import dev.pocketagent.llm.ProviderPreset
import dev.pocketagent.llm.ProviderPresets
import dev.pocketagent.llm.Providers
import dev.pocketagent.setup.AccessibilityState
import dev.pocketagent.setup.CheckMessages
import dev.pocketagent.setup.GuidanceStore
import dev.pocketagent.setup.MicState
import dev.pocketagent.setup.SaveResult
import dev.pocketagent.setup.SetupFacts
import dev.pocketagent.setup.SetupModel
import dev.pocketagent.setup.SetupStep
import dev.pocketagent.setup.StepStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * The launcher screen: a checklist that takes a fresh install to a finished first task (S4 spec §5).
 * It gathers plain facts, hands them to [SetupModel], and draws one card per step. The whole screen is secure
 * so the key field cannot be captured by a screenshot or screen recording.
 */
class SetupActivity : Activity() {
    private val repository by lazy { SetupStores.repository(this) }
    private val settings by lazy { SetupStores.settings(this) }
    private val guidance by lazy { loadGuidance() }

    private lateinit var column: LinearLayout
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var checkJob: Job? = null

    // Drafts survive a redraw. The key draft is only what the user typed; the stored key is never put in a view.
    private var presetId: String? = null
    private var customUrlDraft = "https://"
    private var keyDraft = ""
    private var replacingKey = false
    private var modelDraft: String? = null
    private var imagesDraft: Boolean? = null
    private var testTaskDraft = TEST_TASK_TEXT
    private var expandedOverride: SetupStep? = null
    private var checkMessage: String? = null
    private var checking = false
    private var providerError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val pad = Overlays.dp(this, 16)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(getColor(R.color.brand_background))
            addView(column)
        })
    }

    override fun onResume() {
        super.onResume()
        if (!scope.isActive()) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        render()
    }

    override fun onStop() {
        // Leaving the screen cancels a running check; its result is dropped.
        checkJob?.cancel()
        checkJob = null
        checking = false
        scope.cancel()
        super.onStop()
    }

    private fun CoroutineScope.isActive(): Boolean = coroutineContext[Job]?.isActive == true

    // --- Facts ---

    private fun accessibilityOn(): Boolean = AccessibilityState.isEnabled(
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
        packageName,
        PocketAccessibilityService::class.java.name,
    )

    private fun micState(): MicState = when {
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> MicState.GRANTED
        settings.microphoneAsked -> MicState.DENIED
        else -> MicState.NOT_ASKED
    }

    private fun facts(config: ProviderConfig?): SetupFacts = SetupFacts(
        noticeAccepted = settings.noticeAccepted,
        acceptedCustomHost = settings.acceptedCustomHost,
        config = config,
        modelCheck = if (repository.modelCheckPassed && config != null) CheckResult.Works else null,
        accessibilityOn = accessibilityOn(),
        microphone = micState(),
        bubbleOn = settings.bubbleOn,
        testTaskDone = repository.testTaskDone,
    )

    // --- Drawing ---

    private fun render() {
        val config = repository.load()
        val f = facts(config)
        val eval = SetupModel.evaluate(f)
        column.removeAllViews()

        if (eval.isReady) {
            column.addView(TextView(this).apply {
                setText(R.string.setup_ready_title)
                textSize = 24f
                setTextColor(getColor(R.color.brand_text))
            })
            column.addView(SetupViews.body(this, getString(R.string.setup_ready_body), muted = true))
            column.addView(SetupViews.gap(this, 12))
        } else {
            column.addView(TextView(this).apply {
                setText(R.string.setup_title)
                textSize = 24f
                setTextColor(getColor(R.color.brand_text))
            })
            column.addView(SetupViews.gap(this, 12))
        }

        for (state in eval.steps) {
            val card = StepCard(this, state.step, titleFor(state.step))
            card.setStatus(state.status)
            val expanded = !eval.isReady && state.status == StepStatus.CURRENT ||
                expandedOverride == state.step ||
                (state.step == SetupStep.MICROPHONE && state.status != StepStatus.DONE && !settings.microphoneAsked &&
                    eval.of(SetupStep.ACCESSIBILITY).status == StepStatus.DONE && !eval.isReady)
            card.setExpanded(expanded)
            card.setEditable(!expanded && state.status == StepStatus.DONE && state.step != SetupStep.NOTICE)
            card.onEdit = {
                expandedOverride = state.step
                if (state.step == SetupStep.PROVIDER) replacingKey = false
                render()
            }
            card.setSummary(
                when {
                    state.status == StepStatus.DONE -> summaryFor(state.step, config)
                    state.step == SetupStep.MICROPHONE && settings.microphoneAsked && f.microphone != MicState.GRANTED ->
                        getString(R.string.setup_mic_skipped)
                    state.needsFirst != null && !expanded -> getString(R.string.setup_finish_first, titleFor(state.needsFirst!!))
                    else -> null
                },
            )
            if (expanded) fillBody(card, state.step, config, state.needsFirst)
            column.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = Overlays.dp(this@SetupActivity, 12) })
        }
    }

    private fun titleFor(step: SetupStep): String = getString(
        when (step) {
            SetupStep.NOTICE -> R.string.setup_step_notice
            SetupStep.PROVIDER -> R.string.setup_step_provider
            SetupStep.MODEL_CHECK -> R.string.setup_step_model
            SetupStep.ACCESSIBILITY -> R.string.setup_step_accessibility
            SetupStep.MICROPHONE -> R.string.setup_step_microphone
            SetupStep.BUBBLE -> R.string.setup_step_bubble
            SetupStep.TEST_TASK -> R.string.setup_step_test
        },
    )

    private fun summaryFor(step: SetupStep, config: ProviderConfig?): String? = when (step) {
        SetupStep.NOTICE -> getString(R.string.setup_summary_notice)
        SetupStep.PROVIDER -> config?.let { getString(R.string.setup_summary_provider, it.host) }
        SetupStep.MODEL_CHECK -> config?.let { getString(R.string.setup_summary_model, it.model) }
        SetupStep.ACCESSIBILITY -> getString(R.string.setup_summary_accessibility)
        SetupStep.MICROPHONE -> getString(R.string.setup_summary_microphone)
        SetupStep.BUBBLE -> getString(R.string.setup_summary_bubble)
        SetupStep.TEST_TASK -> getString(R.string.setup_summary_test)
    }

    private fun fillBody(card: StepCard, step: SetupStep, config: ProviderConfig?, needsFirst: SetupStep?) {
        val body = card.body
        when (step) {
            SetupStep.NOTICE -> noticeBody(body)
            SetupStep.PROVIDER -> providerBody(body, config)
            SetupStep.MODEL_CHECK -> modelBody(body, config, needsFirst)
            SetupStep.ACCESSIBILITY -> accessibilityBody(body)
            SetupStep.MICROPHONE -> microphoneBody(body)
            SetupStep.BUBBLE -> bubbleBody(body)
            SetupStep.TEST_TASK -> testTaskBody(body, needsFirst)
        }
    }

    // --- Notice ---

    private fun noticeBody(body: LinearLayout) {
        body.addView(SetupViews.body(this, getString(R.string.notice_body)))
        body.addView(SetupViews.primaryButton(this, getString(R.string.notice_accept)) {
            settings.noticeAccepted = true
            render()
        })
    }

    // --- Provider ---

    private fun selectedPreset(config: ProviderConfig?): ProviderPreset? {
        val id = presetId
        if (id != null) return if (id == CUSTOM) null else ProviderPresets.byId(id)
        return config?.let { c -> ProviderPresets.all.firstOrNull { it.baseUrl == c.baseUrl } }
    }

    private fun isCustom(config: ProviderConfig?): Boolean =
        presetId == CUSTOM || (presetId == null && config != null && selectedPreset(config) == null)

    private fun providerBody(body: LinearLayout, config: ProviderConfig?) {
        val preset = selectedPreset(config)
        val custom = isCustom(config)

        body.addView(SetupViews.body(this, getString(R.string.setup_provider_intro)))
        val presets = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ProviderPresets.all.forEach { p ->
            presets.addView(
                choiceButton(p.label, !custom && preset?.id == p.id) { captureDrafts(); presetId = p.id; imagesDraft = null; render() },
            )
        }
        presets.addView(choiceButton(getString(R.string.setup_provider_custom), custom) {
            captureDrafts(); presetId = CUSTOM; render()
        })
        body.addView(presets)

        var urlField: EditText? = null
        if (custom) {
            urlField = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                hint = getString(R.string.setup_custom_url_hint)
                setText(config?.takeIf { presetId == null }?.baseUrl ?: customUrlDraft)
                tag = TAG_URL
                styleField(this)
            }
            body.addView(SetupViews.body(this, getString(R.string.setup_custom_url_label), muted = true))
            body.addView(urlField)
        }

        val needsKey = preset?.needsKey ?: true
        val keyField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            hint = getString(if (needsKey) R.string.setup_key_hint else R.string.setup_key_hint_optional)
            setText(keyDraft)
            tag = TAG_KEY
            styleField(this)
        }
        val keySaved = config != null && config.apiKey.isNotEmpty() && !replacingKey
        if (keySaved) {
            body.addView(SetupViews.body(this, getString(R.string.setup_key_saved)))
            body.addView(SetupViews.secondaryButton(this, getString(R.string.setup_key_replace)) {
                captureDrafts(); replacingKey = true; keyDraft = ""; render()
            })
        } else {
            body.addView(keyField)
        }

        val errorView = SetupViews.body(this, providerError.orEmpty()).apply {
            setTextColor(getColor(R.color.bubble_error))
            visibility = if (providerError.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        body.addView(errorView)
        body.addView(SetupViews.gap(this))

        body.addView(SetupViews.primaryButton(this, getString(R.string.setup_provider_save)) {
            val kind = preset?.kind ?: ProviderKind.OPENAI_COMPAT
            val baseUrl = (urlField?.text?.toString()?.trim()) ?: preset?.baseUrl.orEmpty()
            val typed = keyField.text.toString().trim()
            val keyText = if (keySaved) config?.apiKey.orEmpty() else typed
            val images = imagesDraft ?: config?.supportsImages ?: preset?.imagesByDefault ?: false
            val draft = ProviderConfig(
                kind = kind, baseUrl = baseUrl, apiKey = keyText,
                model = config?.model ?: modelDraft ?: "",
                supportsImages = images,
                effort = preset?.effort,
            )
            val check = draft.copy(model = draft.model.ifBlank { "model" }).validate()
            if (!check.ok) {
                providerError = check.errors.joinToString(" ")
                keyDraft = typed
                if (urlField != null) customUrlDraft = baseUrl
                render()
                return@primaryButton
            }
            if (SetupModel.needsHostWarning(draft) && settings.acceptedCustomHost != draft.host) {
                // Ask for the real host first; the user saves again after accepting.
                pendingHost = draft.host
                keyDraft = typed
                customUrlDraft = baseUrl
                providerError = getString(R.string.notice_custom_host, draft.host)
                render()
                return@primaryButton
            }
            when (val result = repository.save(draft)) {
                SaveResult.Saved -> {
                    providerError = null
                    keyDraft = ""
                    replacingKey = false
                    expandedOverride = null
                    pendingHost = null
                    checkMessage = null
                }
                is SaveResult.Failed -> providerError = result.reason
            }
            render()
        })

        val host = pendingHost
        if (host != null) {
            body.addView(SetupViews.body(this, getString(R.string.notice_custom_host, host)))
            body.addView(SetupViews.secondaryButton(this, getString(R.string.setup_host_accept)) {
                settings.acceptedCustomHost = host
                pendingHost = null
                providerError = null
                render()
            })
        }
    }

    private var pendingHost: String? = null

    private fun choiceButton(label: String, selected: Boolean, onClick: () -> Unit) =
        if (selected) SetupViews.primaryButton(this, label, onClick) else SetupViews.secondaryButton(this, label, onClick)

    private fun styleField(field: EditText) {
        field.setTextColor(getColor(R.color.brand_text))
        field.setHintTextColor(getColor(R.color.brand_muted))
        field.setSingleLine(true)
    }

    /** Copies what was typed into the drafts, so a redraw does not lose it. */
    private fun captureDrafts() {
        // Fields are rebuilt from the drafts; only the custom URL and key are typed into fields that this screen can read back.
        findFields(column).forEach { f ->
            when (f.tag) {
                TAG_URL -> customUrlDraft = f.text.toString()
                TAG_KEY -> keyDraft = f.text.toString()
                TAG_MODEL -> modelDraft = f.text.toString()
            }
        }
    }

    private fun findFields(v: View): List<EditText> = when (v) {
        is EditText -> listOf(v)
        is android.view.ViewGroup -> (0 until v.childCount).flatMap { findFields(v.getChildAt(it)) }
        else -> emptyList()
    }

    // --- Model ---

    private fun modelBody(body: LinearLayout, config: ProviderConfig?, needsFirst: SetupStep?) {
        if (config == null || needsFirst != null) {
            body.addView(SetupViews.body(this, getString(R.string.setup_finish_first, titleFor(SetupStep.PROVIDER)), muted = true))
            return
        }
        body.addView(SetupViews.body(this, getString(R.string.setup_model_intro)))
        val modelField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = getString(R.string.setup_model_hint)
            filters = arrayOf(InputFilter.LengthFilter(100))
            setText(modelDraft ?: config.model)
            tag = TAG_MODEL
            styleField(this)
        }
        body.addView(modelField)
        val images = Switch(this).apply {
            text = getString(R.string.setup_model_images)
            setTextColor(getColor(R.color.brand_text))
            isChecked = imagesDraft ?: config.supportsImages
            setOnCheckedChangeListener { _, checked -> imagesDraft = checked }
        }
        body.addView(images)

        val status = SetupViews.body(this, checkMessage.orEmpty())
        status.visibility = if (checkMessage == null) View.GONE else View.VISIBLE
        body.addView(status)

        val spinnerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = if (checking) View.VISIBLE else View.GONE
        }
        spinnerRow.addView(ProgressBar(this))
        spinnerRow.addView(SetupViews.secondaryButton(this, getString(R.string.task_cancel)) {
            checkJob?.cancel()
            checkJob = null
            checking = false
            checkMessage = null
            render()
        })
        body.addView(spinnerRow)

        val test = SetupViews.primaryButton(this, getString(R.string.setup_model_test)) {
            val model = modelField.text.toString().trim()
            modelDraft = model
            runCheck(config, model, images.isChecked)
        }
        test.isEnabled = !checking && modelField.text.toString().isNotBlank()
        modelField.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                test.isEnabled = !checking && !s.isNullOrBlank()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        body.addView(test)
    }

    private fun runCheck(config: ProviderConfig, model: String, images: Boolean) {
        val updated = config.copy(model = model, supportsImages = images)
        if (!updated.validate().ok) {
            checkMessage = updated.validate().errors.joinToString(" ")
            render()
            return
        }
        when (val saved = repository.save(updated)) {
            is SaveResult.Failed -> {
                checkMessage = saved.reason
                render()
                return
            }
            SaveResult.Saved -> Unit
        }
        checking = true
        checkMessage = null
        render()
        checkJob?.cancel()
        checkJob = scope.launch {
            val result = try {
                val client = Providers.create(updated, HttpCaller(OkHttpTransport()))
                ProviderCheck.run(client)
            } catch (e: IllegalArgumentException) {
                CheckResult.Other(e.message.orEmpty())
            }
            checking = false
            checkMessage = CheckMessages.messageFor(result)
            if (result == CheckResult.Works) repository.markModelCheckPassed()
            render()
        }
    }

    // --- Accessibility ---

    private fun accessibilityBody(body: LinearLayout) {
        body.addView(SetupViews.body(this, getString(R.string.setup_accessibility_intro)))
        body.addView(SetupViews.primaryButton(this, getString(R.string.setup_accessibility_open)) {
            openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        body.addView(SetupViews.gap(this))
        body.addView(TextView(this).apply {
            setText(R.string.setup_greyed_heading)
            textSize = 16f
            setTextColor(getColor(R.color.brand_text))
        })
        body.addView(SetupViews.body(this, guidance.forManufacturer(Build.MANUFACTURER).body, muted = true))
        body.addView(SetupViews.secondaryButton(this, getString(R.string.setup_app_info)) {
            openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Some builds hide the screen; the guidance text above says where else to look.
        }
    }

    private fun loadGuidance(): GuidanceStore {
        val files = try {
            assets.list(GUIDANCE_DIR).orEmpty().filter { it.endsWith(".md") }.associateWith { name ->
                assets.open("$GUIDANCE_DIR/$name").use { it.readBytes().toString(Charsets.UTF_8) }
            }
        } catch (e: IOException) {
            emptyMap()
        }
        return try {
            GuidanceStore.load(files)
        } catch (e: IllegalArgumentException) {
            GuidanceStore.load(mapOf("generic.md" to getString(R.string.setup_guidance_fallback)))
        }
    }

    // --- Microphone ---

    private fun microphoneBody(body: LinearLayout) {
        body.addView(SetupViews.body(this, getString(R.string.notice_mic)))
        body.addView(SetupViews.primaryButton(this, getString(R.string.setup_mic_allow)) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        })
        body.addView(SetupViews.secondaryButton(this, getString(R.string.setup_mic_skip)) {
            settings.microphoneAsked = true
            render()
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) {
            settings.microphoneAsked = true
            render()
        }
    }

    // --- Bubble and test task ---

    private fun bubbleBody(body: LinearLayout) {
        body.addView(SetupViews.body(this, getString(R.string.setup_bubble_intro)))
        body.addView(Switch(this).apply {
            text = getString(R.string.setup_bubble_switch)
            setTextColor(getColor(R.color.brand_text))
            isChecked = settings.bubbleOn
            setOnCheckedChangeListener { _, checked ->
                settings.bubbleOn = checked
                render()
            }
        })
    }

    private fun testTaskBody(body: LinearLayout, needsFirst: SetupStep?) {
        body.addView(SetupViews.body(this, getString(R.string.setup_test_intro)))
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(InputFilter.LengthFilter(TaskInputActivity.MAX_TEXT))
            setText(testTaskDraft)
            minLines = 2
            setTextColor(getColor(R.color.brand_text))
            setHintTextColor(getColor(R.color.brand_muted))
        }
        body.addView(field)
        if (needsFirst != null) {
            body.addView(SetupViews.body(this, getString(R.string.setup_finish_first, titleFor(needsFirst)), muted = true))
        }
        val host = TaskScreenHost.current
        if (needsFirst == null && host == null) {
            body.addView(SetupViews.body(this, getString(R.string.setup_test_needs_service), muted = true))
        }
        val run = SetupViews.primaryButton(this, getString(R.string.setup_test_run)) {
            val text = field.text.toString().trim()
            testTaskDraft = text
            if (text.isEmpty()) {
                field.error = getString(R.string.task_empty)
                return@primaryButton
            }
            TaskScreenHost.current?.startSetupTask(text)
        }
        run.isEnabled = needsFirst == null && host != null
        body.addView(run)
    }

    companion object {
        const val TEST_TASK_TEXT = "Look at my home screen and name three apps on it"
        private const val CUSTOM = "custom"
        private const val GUIDANCE_DIR = "guidance"
        private const val REQUEST_MIC = 41
        private const val TAG_URL = "url"
        private const val TAG_KEY = "key"
        private const val TAG_MODEL = "model"
    }
}
