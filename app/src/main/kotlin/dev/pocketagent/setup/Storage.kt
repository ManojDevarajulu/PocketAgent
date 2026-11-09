package dev.pocketagent.setup

import dev.pocketagent.llm.ProviderConfig
import java.util.Base64

/** A tiny key-value port so the setup logic stays free of Android types. A null string value removes the key. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

class SecretBoxException(message: String) : Exception(message)

/** Encrypts and decrypts small secrets. There is deliberately no plaintext implementation. */
interface SecretBox {
    /** Returns IV plus ciphertext. Throws [SecretBoxException] when it cannot seal. */
    fun seal(plain: ByteArray): ByteArray

    /** Returns null when the value cannot be opened for any reason. */
    fun open(sealed: ByteArray): ByteArray?
}

/** The text form of sealed bytes: `v1:` plus Base64 on one line. */
object SealedValue {
    private const val PREFIX = "v1:"

    fun encode(bytes: ByteArray): String = PREFIX + Base64.getEncoder().encodeToString(bytes)

    fun decode(text: String?): ByteArray? {
        if (text.isNullOrBlank() || !text.startsWith(PREFIX)) return null
        val bytes = try {
            Base64.getDecoder().decode(text.substring(PREFIX.length))
        } catch (_: IllegalArgumentException) {
            return null
        }
        return bytes.takeIf { it.isNotEmpty() }
    }
}

sealed interface SaveResult {
    data object Saved : SaveResult
    data class Failed(val reason: String) : SaveResult
}

interface ConfigRepository {
    /** Null when nothing is saved, or the key cannot be opened while one is needed. */
    fun load(): ProviderConfig?
    fun save(config: ProviderConfig): SaveResult
    fun clear()
    val modelCheckPassed: Boolean
    fun markModelCheckPassed()
    var testTaskDone: Boolean
}
