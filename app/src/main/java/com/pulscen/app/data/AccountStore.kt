package com.pulscen.app.data

import android.content.Context
import com.pulscen.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.util.Base64

data class LocalAccount(
    val email: String,
    val uid: String,
    val salt: String,
    val iterations: Int
)

data class PendingRegistration(
    val email: String,
    val uid: String,
    val salt: String,
    val iterations: Int,
    val key: ByteArray,
    val code: String,
    val expiresAt: String
) {
    fun isExpired(): Boolean = Dates.parse(expiresAt)?.isBefore(java.time.Instant.now()) ?: true

    fun toJson(): JSONObject = JSONObject()
        .put("email", email)
        .put("uid", uid)
        .put("salt", salt)
        .put("iterations", iterations)
        .put("keyEnc", TokenVault.encrypt(Base64.getEncoder().encodeToString(key)) ?: "")
        .put("code", code)
        .put("expiresAt", expiresAt)

    companion object {
        fun fromJson(json: JSONObject): PendingRegistration? = runCatching {
            val keyText = TokenVault.decrypt(json.optString("keyEnc"))
            if (keyText.isBlank()) return@runCatching null
            PendingRegistration(
                email = json.getString("email"),
                uid = json.getString("uid"),
                salt = json.getString("salt"),
                iterations = json.optInt("iterations", CryptoBox.ITERATIONS),
                key = Base64.getDecoder().decode(keyText),
                code = json.getString("code"),
                expiresAt = json.getString("expiresAt")
            )
        }.getOrNull()
    }
}

class AccountStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun localAccount(): LocalAccount? {
        val email = prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() } ?: return null
        val uid = prefs.getString(KEY_UID, null)?.takeIf { it.isNotBlank() } ?: return null
        val salt = prefs.getString(KEY_SALT, null)?.takeIf { it.isNotBlank() } ?: return null
        return LocalAccount(
            email = email,
            uid = uid,
            salt = salt,
            iterations = prefs.getInt(KEY_ITERATIONS, CryptoBox.ITERATIONS)
        )
    }

    fun saveLocalAccount(account: LocalAccount) {
        prefs.edit()
            .putString(KEY_EMAIL, account.email)
            .putString(KEY_UID, account.uid)
            .putString(KEY_SALT, account.salt)
            .putInt(KEY_ITERATIONS, account.iterations)
            .apply()
    }

    fun hasLocalAccount(): Boolean = localAccount() != null

    fun saveSessionKey(key: ByteArray) {
        TokenVault.encrypt(Base64.getEncoder().encodeToString(key))?.let {
            prefs.edit().putString(KEY_SESSION_KEY, it).apply()
        }
    }

    fun sessionKey(): ByteArray? {
        val encoded = TokenVault.decrypt(prefs.getString(KEY_SESSION_KEY, null))
        if (encoded.isBlank()) return null
        return runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
    }

    fun clearSessionKey() {
        prefs.edit().remove(KEY_SESSION_KEY).apply()
    }

    fun pending(): PendingRegistration? {
        val raw = prefs.getString(KEY_PENDING, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { PendingRegistration.fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun savePending(pending: PendingRegistration) {
        prefs.edit().putString(KEY_PENDING, pending.toJson().toString()).apply()
    }

    fun clearPending() {
        prefs.edit().remove(KEY_PENDING).apply()
    }

    fun envelopeFile(): File {
        val root = File(context.filesDir, "account").apply { mkdirs() }
        return File(root, "bundle.enc")
    }

    fun readEnvelope(): ByteArray? = runCatching {
        envelopeFile().takeIf { it.exists() }?.readBytes()
    }.getOrNull()

    fun writeEnvelope(bytes: ByteArray) {
        val file = envelopeFile()
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "bundle.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            file.writeBytes(bytes)
            tmp.delete()
        }
    }

    fun clearAccountData() {
        prefs.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_UID)
            .remove(KEY_SALT)
            .remove(KEY_ITERATIONS)
            .remove(KEY_SESSION_KEY)
            .remove(KEY_PENDING)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_LAST_MESSAGE)
            .remove(KEY_LAST_SUCCESS)
            .apply()
        envelopeFile().delete()
    }

    fun autoSync(): Boolean = prefs.getBoolean(KEY_AUTO_SYNC, true)

    fun setAutoSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }

    fun lastSync(): String? = prefs.getString(KEY_LAST_SYNC, null)

    fun lastMessage(): String? = prefs.getString(KEY_LAST_MESSAGE, null)

    fun lastSuccess(): Boolean = prefs.getBoolean(KEY_LAST_SUCCESS, false)

    fun setLastResult(message: String, success: Boolean) {
        prefs.edit()
            .putString(KEY_LAST_SYNC, Dates.nowIso())
            .putString(KEY_LAST_MESSAGE, message)
            .putBoolean(KEY_LAST_SUCCESS, success)
            .apply()
    }

    companion object {
        const val PREFS = "puls_cen"
        private const val KEY_EMAIL = "account_email"
        private const val KEY_UID = "account_uid"
        private const val KEY_SALT = "account_salt"
        private const val KEY_ITERATIONS = "account_iterations"
        private const val KEY_SESSION_KEY = "session_key_enc"
        private const val KEY_PENDING = "pending_registration"
        private const val KEY_AUTO_SYNC = "auto_sync"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_LAST_MESSAGE = "last_sync_message"
        private const val KEY_LAST_SUCCESS = "last_sync_success"
    }
}

object VerificationCodes {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun newCode(): String {
        val random = SecureRandom()
        return (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    }

    fun matches(input: String, expected: String): Boolean =
        input.trim().uppercase() == expected.uppercase()
}
