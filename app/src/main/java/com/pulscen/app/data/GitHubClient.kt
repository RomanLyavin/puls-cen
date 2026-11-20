package com.pulscen.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

data class RemoteFile(val text: String, val sha: String)

data class RemoteBytes(val bytes: ByteArray, val sha: String)

class GitHubException(message: String) : Exception(message)

class AccountAlreadyExistsException : Exception("Аккаунт уже существует")

private class MissingShaException(message: String) : Exception(message)

internal fun isMissingShaError(code: Int, sha: String?, responseBody: String): Boolean {
    if (code != 422 || !sha.isNullOrBlank()) return false
    val detail = runCatching { JSONObject(responseBody).optString("message") }.getOrDefault("")
    return detail.contains("sha", ignoreCase = true) &&
        detail.contains("supplied", ignoreCase = true)
}

class GitHubClient(private val config: GitHubConfig) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val request = request("repos/${config.owner}/${config.repo}")
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GitHubException(errorMessage(response.code, body))
            val json = JSONObject(body)
            val permissions = json.optJSONObject("permissions")
            if (permissions != null && permissions.has("push") && !permissions.optBoolean("push")) {
                throw GitHubException("Токен не имеет права записи в этот репозиторий")
            }
            val defaultBranch = json.optString("default_branch", "main")
            "Подключено: ${json.optString("full_name", "${config.owner}/${config.repo}")}, ветка $defaultBranch"
        }
    }

    suspend fun getFile(path: String): RemoteFile? {
        val remote = getFileBytes(path) ?: return null
        return RemoteFile(String(remote.bytes, Charsets.UTF_8), remote.sha)
    }

    suspend fun getFileBytes(path: String): RemoteBytes? = withContext(Dispatchers.IO) {
        val request = request(
            "repos/${config.owner}/${config.repo}/contents/${encodePath(path)}?ref=${config.normalizedBranch}"
        )
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GitHubException(errorMessage(response.code, body))
            val json = JSONObject(body)
            if (json.optString("type") != "file") return@withContext null
            val content = json.optString("content", "")
            val decoded = runCatching {
                Base64.getMimeDecoder().decode(content)
            }.getOrElse { throw GitHubException("Не удалось прочитать $path из GitHub") }
            RemoteBytes(decoded, json.optString("sha", ""))
        }
    }

    suspend fun putFile(
        path: String,
        bytes: ByteArray,
        message: String,
        sha: String? = null,
        createOnly: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        if (config.token.isBlank()) {
            throw GitHubException("Не настроен GitHub-токен. Откройте «Профиль → Синхронизация».")
        }
        try {
            putOnce(path, bytes, message, sha)
        } catch (missingSha: MissingShaException) {
            if (createOnly) throw AccountAlreadyExistsException()
            // Файл уже существует, но мы не знаем его версию. Запрашиваем sha и повторяем один раз.
            val remoteSha = getFileSha(path)
                ?: throw GitHubException(
                    "GitHub: не удалось получить версию файла $path. Повторите синхронизацию."
                )
            putOnce(path, bytes, message, remoteSha)
        }
    }

    private fun putOnce(
        path: String,
        bytes: ByteArray,
        message: String,
        sha: String?
    ): String {
        val payload = JSONObject()
            .put("message", message)
            .put("content", Base64.getEncoder().encodeToString(bytes))
            .put("branch", config.normalizedBranch)
        if (!sha.isNullOrBlank()) payload.put("sha", sha)
        val body = payload.toString().toRequestBody(jsonType)
        val request = Request.Builder()
            .url("https://api.github.com/repos/${config.owner}/${config.repo}/contents/${encodePath(path)}")
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "PulsCen-Android")
            .put(body)
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (isMissingShaError(response.code, sha, responseBody)) {
                    throw MissingShaException(errorMessage(response.code, responseBody))
                }
                throw GitHubException(errorMessage(response.code, responseBody))
            }
            return runCatching { JSONObject(responseBody).getJSONObject("content").optString("sha", "") }
                .getOrDefault("")
        }
    }

    private suspend fun getFileSha(path: String): String? = withContext(Dispatchers.IO) {
        val request = request(
            "repos/${config.owner}/${config.repo}/contents/${encodePath(path)}?ref=${config.normalizedBranch}"
        )
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GitHubException(errorMessage(response.code, body))
            val json = JSONObject(body)
            if (json.optString("type") != "file") return@withContext null
            json.optString("sha", "").ifBlank { null }
        }
    }

    suspend fun putText(
        path: String,
        text: String,
        message: String,
        sha: String? = null,
        createOnly: Boolean = false
    ): String = putFile(path, text.toByteArray(Charsets.UTF_8), message, sha, createOnly)

    fun rawUrl(path: String): String =
        "https://raw.githubusercontent.com/${config.owner}/${config.repo}/${config.normalizedBranch}/${path.trimStart('/')}"

    fun pageUrl(path: String): String = config.pagesUrl + path.trimStart('/')

    private fun request(path: String): Request {
        val builder = Request.Builder()
            .url("https://api.github.com/$path")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "PulsCen-Android")
            .get()
        if (config.token.isNotBlank()) {
            builder.header("Authorization", "Bearer ${config.token}")
        }
        return builder.build()
    }

    private fun encodePath(path: String): String =
        path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun errorMessage(code: Int, body: String): String {
        val apiMessage = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
        val detail = apiMessage.ifBlank { body.take(300) }
        return when {
            code == 401 -> "GitHub: неверный токен (401)"
            code == 403 && detail.contains("rate limit", ignoreCase = true) ->
                "GitHub: превышен лимит запросов, попробуйте позже"
            code == 404 -> "GitHub: репозиторий или файл не найден (404). Проверьте доступ токена."
            code == 409 || code == 422 -> "GitHub: конфликт версий файла. Повторите синхронизацию. $detail"
            else -> "GitHub $code: $detail"
        }
    }
}

fun bundlePath(uid: String): String = "data/u/$uid/bundle.enc"
