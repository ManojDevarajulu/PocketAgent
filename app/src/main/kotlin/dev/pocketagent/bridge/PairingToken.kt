package dev.pocketagent.bridge

import java.security.SecureRandom

/** The shared secret between the phone and the computer: 192 random bits as hex, made on the phone and shown only there. */
object PairingToken {
    fun generate(random: SecureRandom = SecureRandom()): String =
        ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    fun looksValid(token: String?): Boolean = token != null && token.length == 48 && token.all { it in '0'..'9' || it in 'a'..'f' }
}
