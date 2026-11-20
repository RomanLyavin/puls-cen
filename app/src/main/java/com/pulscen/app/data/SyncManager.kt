package com.pulscen.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncOutcome(val success: Boolean, val message: String)

class SyncManager(
    private val repository: AppRepository,
    private val store: AccountStore,
    private val prefs: SyncPrefs
) {
    private fun client(): GitHubClient = GitHubClient(prefs.load())

    suspend fun accountExists(email: String): Boolean = withContext(Dispatchers.IO) {
        val uid = CryptoBox.uid(email)
        client().getFileBytes(bundlePath(uid)) != null
    }

    suspend fun uploadNewAccount(uid: String, encrypted: ByteArray): SyncOutcome =
        withContext(Dispatchers.IO) {
            val config = prefs.load()
            if (config.token.isBlank()) {
                return@withContext SyncOutcome(
                    false,
                    "В приложении не настроен GitHub-токен. Обратитесь к разработчику."
                )
            }
            try {
                client().putFile(
                    path = bundlePath(uid),
                    bytes = encrypted,
                    message = "Create price pulse account",
                    createOnly = true
                )
                store.setLastResult("Аккаунт опубликован на GitHub", true)
                SyncOutcome(true, "Аккаунт создан")
            } catch (exists: AccountAlreadyExistsException) {
                SyncOutcome(false, "Аккаунт с таким email уже существует — войдите")
            } catch (error: Exception) {
                SyncOutcome(false, error.message ?: "Не удалось опубликовать аккаунт")
            }
        }

    suspend fun pullAndMerge(): SyncOutcome = withContext(Dispatchers.IO) {
        val session = repository.session
            ?: return@withContext SyncOutcome(false, "Нужно войти в аккаунт")
        val config = prefs.load()
        if (config.token.isBlank()) {
            return@withContext SyncOutcome(
                false,
                "Не настроен GitHub-токен. Откройте «Профиль → Синхронизация»."
            )
        }
        try {
            val remote = client().getFileBytes(bundlePath(session.uid))
                ?: return@withContext SyncOutcome(
                    false,
                    "Аккаунт ещё не опубликован. Нажмите «Синхронизировать» позже."
                )
            val text = CryptoBox.decrypt(remote.bytes, session.key)
                ?: return@withContext SyncOutcome(
                    false,
                    "Не удалось расшифровать данные. Проверьте пароль или повторите позже."
                )
            val remoteBundle = UserBundle.fromJson(text)
                ?: return@withContext SyncOutcome(false, "Повреждён файл данных на GitHub")
            if (remoteBundle.email != session.email) {
                return@withContext SyncOutcome(false, "Файл на GitHub принадлежит другому аккаунту")
            }
            val local = repository.bundle.value
            val merged = if (local == null) remoteBundle else BundleMerge.merge(local, remoteBundle)
            repository.replaceBundle(merged, if (merged == remoteBundle) remote.bytes else null)
            store.setLastResult("Данные получены с GitHub", true)
            SyncOutcome(true, "Данные обновлены")
        } catch (error: Exception) {
            val message = error.message ?: "Не удалось получить данные с GitHub"
            store.setLastResult(message, false)
            SyncOutcome(false, message)
        }
    }

    suspend fun push(): SyncOutcome = withContext(Dispatchers.IO) {
        val session = repository.session
            ?: return@withContext SyncOutcome(false, "Нужно войти в аккаунт")
        val config = prefs.load()
        if (config.token.isBlank()) {
            return@withContext SyncOutcome(
                false,
                "Не настроен GitHub-токен. Откройте «Профиль → Синхронизация»."
            )
        }
        val path = bundlePath(session.uid)
        var lastError: String? = null
        repeat(2) { attempt ->
            val bytes = repository.encryptedBytes()
                ?: return@withContext SyncOutcome(false, "Нет данных для отправки")
            try {
                val remote = client().getFileBytes(path)
                client().putFile(
                    path = path,
                    bytes = bytes,
                    message = "Update price pulse data",
                    sha = remote?.sha
                )
                store.setLastResult("Синхронизировано", true)
                return@withContext SyncOutcome(true, "Синхронизировано")
            } catch (error: Exception) {
                lastError = error.message ?: "Не удалось отправить данные"
                if (attempt == 0) {
                    // Конфликт версий: перечитываем и объединяем, затем пробуем ещё раз.
                    val pull = pullAndMerge()
                    if (!pull.success && pull.message.contains("поврежд", ignoreCase = true)) {
                        return@withContext SyncOutcome(false, pull.message)
                    }
                }
            }
        }
        store.setLastResult(lastError ?: "Не удалось отправить данные", false)
        SyncOutcome(false, lastError ?: "Не удалось отправить данные")
    }

    suspend fun sync(): SyncOutcome = withContext(Dispatchers.IO) {
        val pull = pullAndMerge()
        if (!pull.success) return@withContext pull
        push()
    }
}
