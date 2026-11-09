package dev.pocketagent.setup

import dev.pocketagent.llm.ProviderConfig
import dev.pocketagent.llm.ProviderKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeStore : KeyValueStore {
    val strings = mutableMapOf<String, String>()
    val booleans = mutableMapOf<String, Boolean>()
    override fun getString(key: String) = strings[key]
    override fun putString(key: String, value: String?) {
        if (value == null) strings.remove(key) else strings[key] = value
    }
    override fun getBoolean(key: String, default: Boolean) = booleans[key] ?: default
    override fun putBoolean(key: String, value: Boolean) {
        booleans[key] = value
    }
}

class XorBox : SecretBox {
    var failToSeal = false
    var failToOpen = false
    override fun seal(plain: ByteArray): ByteArray {
        if (failToSeal) throw SecretBoxException("no keystore")
        return byteArrayOf(7) + ByteArray(plain.size) { (plain[it].toInt() xor 0x5A).toByte() }
    }
    override fun open(sealed: ByteArray): ByteArray? {
        if (failToOpen || sealed.size < 2 || sealed[0] != 7.toByte()) return null
        return ByteArray(sealed.size - 1) { (sealed[it + 1].toInt() xor 0x5A).toByte() }
    }
}

class StoredConfigRepositoryTest {
    private val store = FakeStore()
    private val box = XorBox()
    private val repo = StoredConfigRepository(store, box)
    private val secret = "sk-secret-key-123"

    private fun config(
        key: String = secret,
        model: String = "m1",
        images: Boolean = false,
        baseUrl: String = "https://api.example.com/v1",
        effort: String? = "high",
    ) = ProviderConfig(ProviderKind.ANTHROPIC, baseUrl, key, model, images, 64_000, 2048, effort)

    private fun markProgress() {
        repo.markModelCheckPassed()
        repo.testTaskDone = true
    }

    @Test
    fun savesAndLoadsAConfigIncludingItsKey() {
        val c = config()
        assertEquals(SaveResult.Saved, repo.save(c))
        assertEquals(c, repo.load())
    }

    @Test
    fun theKeyIsNeverStoredInTheClear() {
        repo.save(config())
        assertTrue(store.strings.isNotEmpty())
        assertTrue(store.strings.values.none { secret in it })
    }

    @Test
    fun anUnopenableKeyLoadsAsNoConfig() {
        repo.save(config())
        box.failToOpen = true
        assertNull(repo.load())
    }

    @Test
    fun aTamperedValueLoadsAsNoConfig() {
        repo.save(config())
        store.strings["config.apiKey"] = "v1:AAAA"
        assertNull(repo.load())
        store.strings["config.apiKey"] = "%%% garbage %%%"
        assertNull(repo.load())
    }

    @Test
    fun aSealFailureStoresNothing() {
        box.failToSeal = true
        assertIs<SaveResult.Failed>(repo.save(config()))
        assertNull(repo.load())
        assertTrue(store.strings.keys.none { it.startsWith("config.") })
        assertTrue(store.strings.values.none { secret in it })
    }

    @Test
    fun anEmptyKeyForALocalServerRoundTrips() {
        val c = config(key = "", baseUrl = "http://192.168.1.5:11434/v1")
        assertEquals(SaveResult.Saved, repo.save(c))
        assertFalse(store.strings.containsKey("config.apiKey"))
        assertEquals(c, repo.load())
    }

    @Test
    fun changingTheModelClearsThePassedCheckAndTheTestTask() {
        repo.save(config())
        markProgress()
        repo.save(config(model = "m2"))
        assertFalse(repo.modelCheckPassed)
        assertFalse(repo.testTaskDone)
    }

    @Test
    fun changingTheKeyClearsThem() {
        repo.save(config())
        markProgress()
        repo.save(config(key = "sk-other"))
        assertFalse(repo.modelCheckPassed)
        assertFalse(repo.testTaskDone)
    }

    @Test
    fun changingImagesClearsThem() {
        repo.save(config())
        markProgress()
        repo.save(config(images = true))
        assertFalse(repo.modelCheckPassed)
        assertFalse(repo.testTaskDone)
    }

    @Test
    fun savingTheSameConfigKeepsThem() {
        repo.save(config())
        markProgress()
        repo.save(config())
        assertTrue(repo.modelCheckPassed)
        assertTrue(repo.testTaskDone)
    }

    @Test
    fun clearRemovesEverything() {
        repo.save(config())
        markProgress()
        repo.clear()
        assertNull(repo.load())
        assertFalse(repo.modelCheckPassed)
        assertFalse(repo.testTaskDone)
        assertTrue(store.strings.isEmpty())
    }

    @Test
    fun anUnknownStoredKindLoadsAsNoConfig() {
        repo.save(config())
        store.strings["config.kind"] = "MARTIAN"
        assertNull(repo.load())
    }
}
