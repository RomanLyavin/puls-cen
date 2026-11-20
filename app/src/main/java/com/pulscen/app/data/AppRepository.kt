package com.pulscen.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64

data class Session(
    val email: String,
    val uid: String,
    val key: ByteArray,
    val salt: String,
    val iterations: Int
) {
    fun keyBase64(): String = Base64.getEncoder().encodeToString(key)
}

class AppRepository(private val store: AccountStore) {
    private val _bundle = MutableStateFlow<UserBundle?>(null)
    val bundle: StateFlow<UserBundle?> = _bundle.asStateFlow()

    private var currentSession: Session? = null
    val session: Session? get() = currentSession

    fun hasSession(): Boolean = currentSession != null

    fun openSession(session: Session, bundle: UserBundle) {
        currentSession = session
        _bundle.value = bundle
        store.saveLocalAccount(
            LocalAccount(session.email, session.uid, session.salt, session.iterations)
        )
        store.saveSessionKey(session.key)
        persist()
    }

    fun restoreSession(): Boolean {
        val account = store.localAccount() ?: return false
        val key = store.sessionKey() ?: return false
        val envelope = store.readEnvelope() ?: return false
        val text = CryptoBox.decrypt(envelope, key) ?: return false
        val bundle = UserBundle.fromJson(text) ?: return false
        if (bundle.email != account.email) return false
        currentSession = Session(account.email, account.uid, key, account.salt, account.iterations)
        _bundle.value = bundle
        return true
    }

    fun update(transform: (UserBundle) -> UserBundle): UserBundle? {
        val current = _bundle.value ?: return null
        val updated = transform(current).touch()
        _bundle.value = updated
        persist()
        return updated
    }

    /** Заменяет локальную копию расшифрованным удалённым бандлом. */
    fun replaceBundle(bundle: UserBundle, envelopeBytes: ByteArray? = null) {
        _bundle.value = bundle
        if (envelopeBytes != null) {
            store.writeEnvelope(envelopeBytes)
        } else {
            persist()
        }
    }

    /** Сохраняет текущий бандл и возвращает свежий шифрованный файл. */
    fun persist(): ByteArray? {
        val session = currentSession ?: return null
        val bundle = _bundle.value ?: return null
        val bytes = CryptoBox.encrypt(
            bundle.toJson().toString(),
            session.key,
            session.salt,
            session.iterations
        )
        store.writeEnvelope(bytes)
        return bytes
    }

    fun encryptedBytes(): ByteArray? {
        val session = currentSession ?: return null
        val bundle = _bundle.value ?: return null
        return CryptoBox.encrypt(
            bundle.toJson().toString(),
            session.key,
            session.salt,
            session.iterations
        )
    }

    fun decryptRemote(envelopeBytes: ByteArray): String? {
        val session = currentSession ?: return null
        return CryptoBox.decrypt(envelopeBytes, session.key)
    }

    fun closeSession() {
        currentSession = null
        _bundle.value = null
        store.clearSessionKey()
    }

    fun logout() {
        closeSession()
        store.clearAccountData()
    }

    fun hasLocalData(): Boolean = store.readEnvelope() != null && store.localAccount() != null
}
