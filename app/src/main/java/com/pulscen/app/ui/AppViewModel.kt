package com.pulscen.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulscen.app.BuildConfig
import com.pulscen.app.PulsCenApplication
import com.pulscen.app.data.Dates
import com.pulscen.app.data.DeletedProduct
import com.pulscen.app.data.FetchedProduct
import com.pulscen.app.data.Marketplace
import com.pulscen.app.data.PendingRegistration
import com.pulscen.app.data.PriceFetcher
import com.pulscen.app.data.PricePoint
import com.pulscen.app.data.Product
import com.pulscen.app.data.UserBundle
import com.pulscen.app.update.ApkVerifier
import com.pulscen.app.update.GitHubRelease
import com.pulscen.app.update.GitHubUpdate
import com.pulscen.app.update.UpdateState
import com.pulscen.app.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface Screen {
    data object Auth : Screen
    data object Verify : Screen
    data object Home : Screen
    data class ProductDetails(val id: String) : Screen
    data object Profile : Screen
}

data class SyncUi(
    val running: Boolean = false,
    val message: String? = null,
    val success: Boolean = true,
    val lastSync: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as PulsCenApplication).container
    private val repository = container.repository
    private val store = container.accountStore
    private val prefs = container.syncPrefs

    val bundle: StateFlow<UserBundle?> = repository.bundle

    private val _screen = MutableStateFlow<Screen>(Screen.Auth)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _pending = MutableStateFlow<PendingRegistration?>(null)
    val pending: StateFlow<PendingRegistration?> = _pending.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _sync = MutableStateFlow(
        SyncUi(
            message = store.lastMessage(),
            success = store.lastSuccess(),
            lastSync = store.lastSync()
        )
    )
    val sync: StateFlow<SyncUi> = _sync.asStateFlow()

    private val _addLoading = MutableStateFlow(false)
    val addLoading: StateFlow<Boolean> = _addLoading.asStateFlow()

    private val _addError = MutableStateFlow<String?>(null)
    val addError: StateFlow<String?> = _addError.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _refreshingId = MutableStateFlow<String?>(null)
    val refreshingId: StateFlow<String?> = _refreshingId.asStateFlow()

    val autoSync = MutableStateFlow(store.autoSync())

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val siteUrl: String get() = prefs.load().pagesUrl
    val tokenConfigured: Boolean get() = prefs.load().token.isNotBlank()
    val hasCustomToken: Boolean get() = prefs.load().hasCustomToken

    private val priceFetcher = PriceFetcher()

    init {
        if (repository.restoreSession()) {
            _screen.value = Screen.Home
            afterLogin()
        } else {
            _pending.value = store.pending()
            if (_pending.value != null) _screen.value = Screen.Verify
        }
    }

    // ---------- Авторизация ----------

    fun login(email: String, password: String) {
        if (_authLoading.value) return
        _authLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            val error = container.authManager.login(email, password)
            _authLoading.value = false
            if (error == null) {
                _pending.value = null
                _screen.value = Screen.Home
                WorkScheduler.schedule(getApplication())
                afterLogin()
            } else {
                _authError.value = error
            }
        }
    }

    fun register(email: String, password: String, confirm: String) {
        if (_authLoading.value) return
        _authLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            val error = container.authManager.register(email, password, confirm)
            _authLoading.value = false
            if (error == null) {
                _pending.value = store.pending()
                _screen.value = Screen.Verify
            } else {
                _authError.value = error
            }
        }
    }

    fun verify(code: String) {
        if (_authLoading.value) return
        _authLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            val error = container.authManager.verify(code)
            _authLoading.value = false
            if (error == null) {
                _pending.value = null
                _screen.value = Screen.Home
                WorkScheduler.schedule(getApplication())
                afterLogin()
            } else {
                _authError.value = error
            }
        }
    }

    fun resendCode() {
        _authError.value = container.authManager.resendCode()
        _pending.value = store.pending()
        if (_authError.value == null) _message.value = "Код обновлён"
    }

    fun cancelVerification() {
        container.authManager.cancelPending()
        _pending.value = null
        _authError.value = null
        _screen.value = Screen.Auth
    }

    fun logout() {
        container.authManager.logout()
        _screen.value = Screen.Auth
        _pending.value = null
        _message.value = null
        _authError.value = null
    }

    fun clearAuthError() {
        _authError.value = null
    }

    // ---------- Профиль ----------

    fun saveProfile(name: String, avatarDataUrl: String?) {
        viewModelScope.launch {
            val error = container.authManager.updateProfile(name, avatarDataUrl)
            _message.value = error ?: "Профиль сохранён"
            if (error == null) syncIfEnabled()
        }
    }

    fun setAutoSync(enabled: Boolean) {
        store.setAutoSync(enabled)
        autoSync.value = enabled
    }

    fun saveToken(token: String) {
        prefs.saveToken(token)
        _message.value = if (token.isBlank()) "Пользовательский токен удалён" else "Токен сохранён"
    }

    // ---------- Товары ----------

    fun openProduct(id: String) {
        _screen.value = Screen.ProductDetails(id)
    }

    fun openProfile() {
        _screen.value = Screen.Profile
    }

    fun goHome() {
        _screen.value = Screen.Home
        _addError.value = null
    }

    fun clearAddError() {
        _addError.value = null
    }

    fun addProduct(url: String, manualTitle: String? = null, manualPrice: Double? = null) {
        if (_addLoading.value) return
        val normalized = url.trim()
        if (normalized.isBlank()) {
            _addError.value = "Вставьте ссылку на товар"
            return
        }
        if (!normalized.startsWith("https://", ignoreCase = true)) {
            _addError.value = "Ссылка должна начинаться с https://"
            return
        }
        val current = repository.bundle.value
        if (current != null && current.products.any { it.url.trim() == normalized }) {
            _addError.value = "Этот товар уже отслеживается"
            return
        }
        _addLoading.value = true
        _addError.value = null
        viewModelScope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) { priceFetcher.fetch(normalized) }
                val product = newProduct(normalized, fetched)
                repository.update { it.copy(products = it.products + product) }
                _message.value = "Товар добавлен"
                _screen.value = Screen.ProductDetails(product.id)
                syncIfEnabled()
            } catch (error: Exception) {
                val message = error.message ?: "Не удалось получить цену"
                if (manualTitle != null && manualPrice != null) {
                    if (manualTitle.isBlank() || manualPrice <= 0.0) {
                        _addError.value = "Введите название и цену"
                    } else {
                        addProductManual(normalized, manualTitle, manualPrice)
                    }
                } else {
                    _addError.value = message
                }
            } finally {
                _addLoading.value = false
            }
        }
    }

    fun addProductManual(url: String, title: String, price: Double) {
        val normalized = url.trim()
        if (normalized.isBlank() || !normalized.startsWith("https://", ignoreCase = true)) {
            _addError.value = "Вставьте корректную ссылку"
            return
        }
        if (title.isBlank()) {
            _addError.value = "Введите название товара"
            return
        }
        if (price <= 0.0 || price >= 1_000_000_000.0) {
            _addError.value = "Введите корректную цену"
            return
        }
        val marketplace = Marketplace.detect(normalized)
        val fetched = FetchedProduct(
            title = title.trim().take(200),
            price = price,
            imageUrl = null,
            article = com.pulscen.app.data.PriceParser.extractArticle(normalized, marketplace),
            marketplace = marketplace
        )
        val product = newProduct(normalized, fetched)
        repository.update { it.copy(products = it.products + product) }
        _addError.value = null
        _message.value = "Товар добавлен с ручной ценой"
        _screen.value = Screen.ProductDetails(product.id)
        syncIfEnabled()
    }

    private fun newProduct(url: String, fetched: FetchedProduct): Product {
        val now = Dates.nowIso()
        return Product(
            id = UUID.randomUUID().toString(),
            url = url,
            title = fetched.title,
            imageUrl = fetched.imageUrl,
            article = fetched.article,
            marketplace = fetched.marketplace,
            currency = fetched.currency,
            addedAt = now,
            lastCheckedAt = now,
            prices = listOf(PricePoint(now, fetched.price))
        )
    }

    fun deleteProduct(id: String) {
        repository.update { bundle ->
            bundle.copy(
                products = bundle.products.filterNot { it.id == id },
                deletedIds = bundle.deletedIds + DeletedProduct(id)
            )
        }
        _screen.value = Screen.Home
        _message.value = "Товар удалён"
        syncIfEnabled()
    }

    fun refreshAll() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                val summary = container.priceRefresher.refreshAll()
                _message.value = when {
                    summary.checked == 0 -> "Нет товаров для обновления"
                    summary.failed == 0 -> "Цены обновлены: ${summary.checked}"
                    else -> "Обновлено ${summary.checked - summary.failed}, ошибок ${summary.failed}"
                }
                syncIfEnabled()
            } catch (error: Exception) {
                _message.value = error.message ?: "Не удалось обновить цены"
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun refreshProduct(id: String) {
        if (_refreshingId.value != null) return
        _refreshingId.value = id
        viewModelScope.launch {
            try {
                container.priceRefresher.refresh(id)
                _message.value = "Цена обновлена"
                syncIfEnabled()
            } catch (error: Exception) {
                _message.value = error.message ?: "Не удалось обновить цену"
            } finally {
                _refreshingId.value = null
            }
        }
    }

    fun setManualPrice(id: String, price: Double) {
        val error = container.priceRefresher.setManualPrice(id, price)
        _message.value = error ?: "Цена записана"
        if (error == null) syncIfEnabled()
    }

    // ---------- Синхронизация ----------

    fun syncNow() {
        if (_sync.value.running) return
        _sync.value = _sync.value.copy(running = true)
        viewModelScope.launch {
            val outcome = container.syncManager.sync()
            _sync.value = SyncUi(
                running = false,
                message = outcome.message,
                success = outcome.success,
                lastSync = store.lastSync()
            )
            _message.value = outcome.message
        }
    }

    private fun syncIfEnabled() {
        if (!autoSync.value || !prefs.load().isConfigured) return
        viewModelScope.launch {
            val outcome = container.syncManager.push()
            _sync.value = SyncUi(
                running = false,
                message = outcome.message,
                success = outcome.success,
                lastSync = store.lastSync()
            )
        }
    }

    private fun afterLogin() {
        viewModelScope.launch {
            if (autoSync.value && prefs.load().isConfigured) {
                val pull = container.syncManager.pullAndMerge()
                _sync.value = SyncUi(
                    running = false,
                    message = pull.message,
                    success = pull.success,
                    lastSync = store.lastSync()
                )
            }
            val stale = repository.bundle.value?.products?.any {
                Dates.isOlderThanHours(it.lastCheckedAt, 6)
            } == true
            if (stale && !_refreshing.value) {
                _refreshing.value = true
                try {
                    val summary = container.priceRefresher.refreshAll()
                    if (summary.failed == 0 && summary.checked > 0 && autoSync.value) {
                        container.syncManager.push()
                    }
                } catch (_: Exception) {
                } finally {
                    _refreshing.value = false
                }
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ---------- Обновления ----------

    fun checkForUpdates() {
        when (_updateState.value) {
            is UpdateState.Checking, is UpdateState.Downloading -> return
            else -> Unit
        }
        val currentVersion = BuildConfig.VERSION_NAME
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    GitHubUpdate.fetchLatest(currentVersion)
                }
                _updateState.value = when {
                    !GitHubUpdate.isNewer(release.version, currentVersion) ->
                        UpdateState.UpToDate(currentVersion)
                    release.apkUrl.isNullOrBlank() -> UpdateState.Error(
                        title = "Обновление недоступно",
                        message = "В последнем релизе на GitHub не найден APK-файл.",
                        retry = UpdateState.Retry.Check
                    )
                    else -> UpdateState.Available(release)
                }
            } catch (error: Exception) {
                _updateState.value = UpdateState.Error(
                    title = "Не удалось проверить обновления",
                    message = "Не удалось получить данные с GitHub. Проверьте подключение к интернету.",
                    retry = UpdateState.Retry.Check
                )
            }
        }
    }

    fun downloadUpdate(release: GitHubRelease) {
        val url = release.apkUrl ?: return
        if (_updateState.value is UpdateState.Downloading) return
        val currentVersion = BuildConfig.VERSION_NAME
        val app = getApplication<Application>()
        _updateState.value = UpdateState.Downloading(release.version, 0)
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val directory = File(app.cacheDir, "updates").apply { mkdirs() }
                    directory.listFiles()?.forEach { it.delete() }
                    val target = File(directory, "PulsCen-${release.version}.apk")
                    GitHubUpdate.downloadApk(
                        url = url,
                        target = target,
                        expectedSize = release.apkSize,
                        currentVersion = currentVersion
                    ) { percent ->
                        _updateState.value = UpdateState.Downloading(release.version, percent)
                    }
                    val archiveVersion = ApkVerifier.versionName(app, target)
                    if (!ApkVerifier.isSameApp(app, target) || archiveVersion != release.version) {
                        target.delete()
                        throw InvalidUpdateApkException()
                    }
                    target
                }
                _updateState.value = UpdateState.Ready(release, file)
            } catch (error: InvalidUpdateApkException) {
                _updateState.value = UpdateState.Error(
                    title = "Не удалось скачать обновление",
                    message = "Скачанный файл не подходит: это не APK «Пульса цен» " +
                        "или его подпись не совпадает с установленной версией.",
                    retry = UpdateState.Retry.Download(release)
                )
            } catch (error: Exception) {
                _updateState.value = UpdateState.Error(
                    title = "Не удалось скачать обновление",
                    message = "Не удалось скачать APK. Проверьте подключение к интернету.",
                    retry = UpdateState.Retry.Download(release)
                )
            }
        }
    }

    fun retryUpdate() {
        when (val state = _updateState.value) {
            is UpdateState.Error -> when (val retry = state.retry) {
                is UpdateState.Retry.Check -> checkForUpdates()
                is UpdateState.Retry.Download -> downloadUpdate(retry.release)
            }
            else -> Unit
        }
    }

    fun dismissUpdate() {
        if (_updateState.value is UpdateState.Downloading) return
        _updateState.value = UpdateState.Idle
    }

    fun consumeReadyUpdate() {
        if (_updateState.value is UpdateState.Ready) _updateState.value = UpdateState.Idle
    }
}

private class InvalidUpdateApkException : Exception()
