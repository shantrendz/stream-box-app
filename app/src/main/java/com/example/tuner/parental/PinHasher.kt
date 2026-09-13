package com.example.tuner.parental

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for the 4-digit parent PIN. A 4-digit PIN can be brute-forced offline
 * no matter what — this only keeps it out of plain text on disk. HMAC-SHA1 and hex encoding
 * because minSdk 24 has neither PBKDF2WithHmacSHA256 nor java.util.Base64.
 */
object PinHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA1"
    private const val ITERATIONS = 10_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    private val secureRandom = SecureRandom()

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it in '0'..'9' }

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }

    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: String, salt: ByteArray, expectedHash: ByteArray): Boolean =
        MessageDigest.isEqual(hash(pin, salt), expectedHash)

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have an even length" }
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
