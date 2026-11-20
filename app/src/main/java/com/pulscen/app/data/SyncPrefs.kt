package com.pulscen.app.data

import android.content.Context
import com.pulscen.app.BuildConfig
import java.util.Base64

data class GitHubConfig(
    val owner: String = BuildConfig.GITHUB_OWNER,
    val repo: String = BuildConfig.GITHUB_REPO,
    val branch: String = "main",
    val token: String = "",
    val hasCustomToken: Boolean = false
) {
    val isConfigured: Boolean get() = owner.isNotBlank() && repo.isNotBlank() && token.isNotBlank()

    val pagesUrl: String get() = "https://${owner.lowercase()}.github.io/$repo/"

    val repoUrl: String get() = "https://github.com/$owner/$repo"

    val normalizedBranch: String get() = branch.trim().ifBlank { "main" }
}

class SyncPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences(AccountStore.PREFS, Context.MODE_PRIVATE)

    fun load(): GitHubConfig {
        val customToken = TokenVault.decrypt(prefs.getString(KEY_TOKEN, null))
        return GitHubConfig(
            token = customToken.ifBlank { embeddedToken() },
            branch = prefs.getString(KEY_BRANCH, "main") ?: "main",
            hasCustomToken = customToken.isNotBlank()
        )
    }

    fun embeddedToken(): String = runCatching {
        val encoded = BuildConfig.GITHUB_TOKEN_B64
        if (encoded.isBlank()) {
            ""
        } else {
            val masked = Base64.getDecoder().decode(encoded)
            val bytes = ByteArray(masked.size) { index -> (masked[index].toInt() xor 0x5A).toByte() }
            String(bytes, Charsets.UTF_8)
        }
    }.getOrDefault("")

    fun saveToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isBlank()) {
            prefs.edit().remove(KEY_TOKEN).apply()
        } else {
            TokenVault.encrypt(trimmed)?.let { prefs.edit().putString(KEY_TOKEN, it).apply() }
        }
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val KEY_TOKEN = "github_token_enc"
        private const val KEY_BRANCH = "github_branch"
    }
}
