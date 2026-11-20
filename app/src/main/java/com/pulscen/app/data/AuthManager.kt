package com.pulscen.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class AuthManager(
    private val repository: AppRepository,
    private val store: AccountStore,
    private val syncManager: SyncManager,
    private val prefs: SyncPrefs
) {
    suspend fun register(email: String, password: String, confirm: String): String? {
        val normalized = CryptoBox.normalizeEmail(email)
        if (!CryptoBox.isValidEmail(normalized)) return "Введите корректный email"
        if (password.length < 6) return "Пароль должен быть не короче 6 символов"
        if (password != confirm) return "Пароли не совпадают"

        val local = store.localAccount()
        if (local != null && local.email == normalized) {
            return "Аккаунт уже сохранён на устройстве — войдите"
        }

        val exists = try {
            syncManager.accountExists(normalized)
        } catch (error: Exception) {
            return "Не удалось проверить аккаунт на GitHub: ${error.message ?: "сетевая ошибка"}"
        }
        if (exists) return "Аккаунт с таким email уже зарегистрирован — войдите"

        val salt = CryptoBox.newSalt()
        val key = withContext(Dispatchers.Default) {
            CryptoBox.deriveKey(password, salt, CryptoBox.ITERATIONS)
        }
        val pending = PendingRegistration(
            email = normalized,
            uid = CryptoBox.uid(normalized),
            salt = salt,
            iterations = CryptoBox.ITERATIONS,
            key = key,
            code = VerificationCodes.newCode(),
            expiresAt = Instant.now().plusSeconds(15 * 60).toString()
        )
        store.savePending(pending)
        return null
    }

    fun resendCode(): String? {
        val pending = store.pending() ?: return "Нет ожидающей регистрации"
        store.savePending(
            pending.copy(
                code = VerificationCodes.newCode(),
                expiresAt = Instant.now().plusSeconds(15 * 60).toString()
            )
        )
        return null
    }

    fun cancelPending() {
        store.clearPending()
    }

    suspend fun verify(code: String): String? {
        val pending = store.pending() ?: return "Нет ожидающей регистрации"
        if (!VerificationCodes.matches(code, pending.code)) return "Неверный код"
        if (pending.isExpired()) return "Код истёк. Отправьте новый"

        val displayName = pending.email.substringBefore("@")
            .replaceFirstChar { it.uppercase() }
            .ifBlank { "Пользователь" }
        val bundle = UserBundle(
            email = pending.email,
            profile = Profile(name = displayName)
        )
        val encrypted = withContext(Dispatchers.Default) {
            CryptoBox.encrypt(
                bundle.toJson().toString(),
                pending.key,
                pending.salt,
                pending.iterations
            )
        }
        val upload = syncManager.uploadNewAccount(pending.uid, encrypted)
        if (!upload.success) return upload.message

        repository.openSession(
            Session(pending.email, pending.uid, pending.key, pending.salt, pending.iterations),
            bundle
        )
        store.clearPending()
        return null
    }

    suspend fun login(email: String, password: String): String? {
        val normalized = CryptoBox.normalizeEmail(email)
        if (!CryptoBox.isValidEmail(normalized)) return "Введите корректный email"
        if (password.isBlank()) return "Введите пароль"

        val local = store.localAccount()
        if (local != null && local.email == normalized) {
            val localEnvelope = store.readEnvelope()
            if (localEnvelope != null) {
                val unlocked = unlock(localEnvelope, password)
                if (unlocked != null) {
                    repository.openSession(
                        Session(
                            normalized,
                            local.uid,
                            unlocked.first,
                            unlocked.second,
                            unlocked.third
                        ),
                        unlocked.fourth
                    )
                    return null
                }
            }
        }

        val config = prefs.load()
        val remote = try {
            GitHubClient(config).getFileBytes(bundlePath(CryptoBox.uid(normalized)))
        } catch (error: Exception) {
            return "Не удалось войти: ${error.message ?: "сетевая ошибка"}"
        } ?: return "Аккаунт не найден. Зарегистрируйтесь в приложении."

        val unlocked = unlock(remote.bytes, password)
            ?: return "Неверный пароль"
        if (unlocked.fourth.email != normalized) {
            return "Файл аккаунта повреждён или относится к другому email"
        }
        repository.openSession(
            Session(normalized, CryptoBox.uid(normalized), unlocked.first, unlocked.second, unlocked.third),
            unlocked.fourth
        )
        return null
    }

    suspend fun updateProfile(name: String, avatarDataUrl: String?): String? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return "Введите имя"
        val bundle = repository.bundle.value ?: return "Профиль не найден"
        repository.update {
            it.copy(
                profile = it.profile.copy(
                    name = trimmed.take(60),
                    avatarDataUrl = when {
                        avatarDataUrl == null -> it.profile.avatarDataUrl
                        avatarDataUrl.isBlank() -> null
                        else -> avatarDataUrl
                    },
                    updatedAt = Dates.nowIso()
                )
            )
        }
        return null
    }

    fun logout() {
        repository.logout()
    }

    private suspend fun unlock(
        envelopeBytes: ByteArray,
        password: String
    ): Quadruple<ByteArray, String, Int, UserBundle>? = withContext(Dispatchers.Default) {
        val envelope = CryptoBox.parseEnvelope(envelopeBytes) ?: return@withContext null
        val key = runCatching {
            CryptoBox.deriveKey(password, envelope.salt, envelope.iterations)
        }.getOrNull() ?: return@withContext null
        val text = CryptoBox.decrypt(envelopeBytes, key) ?: return@withContext null
        val bundle = UserBundle.fromJson(text) ?: return@withContext null
        Quadruple(key, envelope.salt, envelope.iterations, bundle)
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
