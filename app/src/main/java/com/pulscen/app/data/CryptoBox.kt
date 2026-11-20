package com.pulscen.app.data

import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Формат encrypted-файла «Пульса цен».
 *
 * Ключ никогда не попадает в репозиторий: он выводится из пароля пользователя
 * через PBKDF2-HMAC-SHA256 и используется для AES-256-GCM. В публичном
 * репозитории лежит только JSON-конверт с солью, IV и шифротекстом.
 */
object CryptoBox {
    const val FORMAT = "PULS-CEN-1"
    const val KDF = "PBKDF2-HMAC-SHA256"
    const val ITERATIONS = 210_000
    const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    data class Envelope(
        val iterations: Int,
        val salt: String,
        val iv: String,
        val data: String
    )

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun deriveKey(password: String, saltB64: String, iterations: Int = ITERATIONS): ByteArray {
        val salt = decode(saltB64) ?: throw IllegalArgumentException("Некорректная соль")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plain: String, key: ByteArray, saltB64: String, iterations: Int = ITERATIONS): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val envelope = JSONObject()
            .put("format", FORMAT)
            .put("kdf", KDF)
            .put("iterations", iterations)
            .put("salt", saltB64)
            .put("iv", Base64.getEncoder().encodeToString(iv))
            .put("data", Base64.getEncoder().encodeToString(encrypted))
        return envelope.toString().toByteArray(Charsets.UTF_8)
    }

    fun parseEnvelope(payload: ByteArray): Envelope? = runCatching {
        val json = JSONObject(String(payload, Charsets.UTF_8))
        if (json.optString("format") != FORMAT) return@runCatching null
        val iterations = json.optInt("iterations", ITERATIONS)
        if (iterations < 10_000) return@runCatching null
        Envelope(
            iterations = iterations,
            salt = json.getString("salt"),
            iv = json.getString("iv"),
            data = json.getString("data")
        )
    }.getOrNull()

    fun decrypt(payload: ByteArray, key: ByteArray): String? = runCatching {
        val envelope = parseEnvelope(payload) ?: return@runCatching null
        val iv = decode(envelope.iv) ?: return@runCatching null
        val encrypted = decode(envelope.data) ?: return@runCatching null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    fun decryptWithPassword(payload: ByteArray, password: String): String? {
        val envelope = parseEnvelope(payload) ?: return null
        val key = runCatching { deriveKey(password, envelope.salt, envelope.iterations) }.getOrNull()
            ?: return null
        return decrypt(payload, key)
    }

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    private val emailRegex = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    fun isValidEmail(email: String): Boolean = emailRegex.matches(normalizeEmail(email))

    fun uid(email: String): String = sha256Hex(normalizeEmail(email))

    fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun decode(value: String): ByteArray? = runCatching {
        val normalized = value.trim()
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        runCatching { Base64.getDecoder().decode(padded) }
            .getOrElse { Base64.getUrlDecoder().decode(padded) }
    }.getOrNull()
}
