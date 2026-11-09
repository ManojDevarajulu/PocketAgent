package dev.pocketagent.android

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.pocketagent.setup.SecretBox
import dev.pocketagent.setup.SecretBoxException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM with a non-exportable key in the Android Keystore. Bytes in, bytes out; there is no plaintext fallback. */
class KeystoreSecretBox(private val alias: String = "pocketagent.apikey.v1") : SecretBox {
    override fun seal(plain: ByteArray): ByteArray {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keyOrCreate())
            return cipher.iv + cipher.doFinal(plain)
        } catch (_: GeneralSecurityException) {
            throw SecretBoxException("The phone's secure storage could not protect the key.")
        } catch (_: IllegalArgumentException) {
            throw SecretBoxException("The phone's secure storage could not protect the key.")
        } catch (_: ProviderException) {
            throw SecretBoxException("The phone's secure storage could not protect the key.")
        }
    }

    override fun open(sealed: ByteArray): ByteArray? {
        if (sealed.size <= IV_BYTES) return null
        return try {
            val key = existingKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ProviderException) {
            null
        }
    }

    private fun store(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun existingKey(): SecretKey? = store().getKey(alias, null) as? SecretKey

    private fun keyOrCreate(): SecretKey {
        existingKey()?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
