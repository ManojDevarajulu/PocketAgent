package dev.pocketagent.setup

import dev.pocketagent.llm.ProviderConfig
import dev.pocketagent.llm.ProviderKind

/**
 * Keeps the provider config in a [KeyValueStore]. The API key only ever reaches the store sealed by the
 * [SecretBox]; if sealing fails nothing is written (no plaintext fallback), and a value that cannot be opened
 * means "no config", never a crash.
 */
class StoredConfigRepository(
    private val store: KeyValueStore,
    private val box: SecretBox,
) : ConfigRepository {

    override fun load(): ProviderConfig? {
        val kind = store.getString(KIND)?.let { name -> ProviderKind.entries.firstOrNull { it.name == name } }
            ?: return null
        val key = when (val sealed = store.getString(API_KEY)) {
            null -> ""
            else -> {
                val bytes = SealedValue.decode(sealed) ?: return null
                val plain = try {
                    box.open(bytes)
                } catch (_: Exception) {
                    null
                } ?: return null
                String(plain, Charsets.UTF_8)
            }
        }
        return ProviderConfig(
            kind = kind,
            baseUrl = store.getString(BASE_URL) ?: return null,
            apiKey = key,
            model = store.getString(MODEL) ?: return null,
            supportsImages = store.getString(IMAGES)?.toBooleanStrictOrNull() ?: return null,
            contextTokens = store.getString(CONTEXT)?.toIntOrNull() ?: return null,
            maxOutputTokens = store.getString(MAX_OUTPUT)?.toIntOrNull() ?: return null,
            effort = store.getString(EFFORT),
        )
    }

    override fun save(config: ProviderConfig): SaveResult {
        val sealedKey: String? = if (config.apiKey.isEmpty()) {
            null
        } else {
            try {
                SealedValue.encode(box.seal(config.apiKey.toByteArray(Charsets.UTF_8)))
            } catch (e: SecretBoxException) {
                return SaveResult.Failed(e.message ?: "The key could not be encrypted.")
            }
        }
        val previous = load()
        val changed = previous == null ||
            previous.kind != config.kind ||
            previous.baseUrl != config.baseUrl ||
            previous.apiKey != config.apiKey ||
            previous.model != config.model ||
            previous.supportsImages != config.supportsImages
        store.putString(KIND, config.kind.name)
        store.putString(BASE_URL, config.baseUrl)
        store.putString(MODEL, config.model)
        store.putString(IMAGES, config.supportsImages.toString())
        store.putString(EFFORT, config.effort)
        store.putString(CONTEXT, config.contextTokens.toString())
        store.putString(MAX_OUTPUT, config.maxOutputTokens.toString())
        store.putString(ID, "default")
        store.putString(API_KEY, sealedKey)
        if (changed) resetProgress()
        return SaveResult.Saved
    }

    override fun clear() {
        listOf(KIND, BASE_URL, MODEL, IMAGES, EFFORT, CONTEXT, MAX_OUTPUT, ID, API_KEY)
            .forEach { store.putString(it, null) }
        resetProgress()
    }

    override val modelCheckPassed: Boolean get() = store.getBoolean(CHECK_PASSED, false)

    override fun markModelCheckPassed() = store.putBoolean(CHECK_PASSED, true)

    override var testTaskDone: Boolean
        get() = store.getBoolean(TEST_TASK_DONE, false)
        set(value) = store.putBoolean(TEST_TASK_DONE, value)

    private fun resetProgress() {
        store.putBoolean(CHECK_PASSED, false)
        store.putBoolean(TEST_TASK_DONE, false)
    }

    private companion object {
        const val KIND = "config.kind"
        const val BASE_URL = "config.baseUrl"
        const val MODEL = "config.model"
        const val IMAGES = "config.images"
        const val EFFORT = "config.effort"
        const val CONTEXT = "config.contextTokens"
        const val MAX_OUTPUT = "config.maxOutputTokens"
        const val ID = "config.id"
        const val API_KEY = "config.apiKey"
        const val CHECK_PASSED = "check.passed"
        const val TEST_TASK_DONE = "testTask.done"
    }
}
