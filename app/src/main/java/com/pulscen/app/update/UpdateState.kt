package com.pulscen.app.update

import java.io.File

/** UI state of the manual update check. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val release: GitHubRelease) : UpdateState
    data class Downloading(val version: String, val percent: Int) : UpdateState
    data class Ready(val release: GitHubRelease, val file: File) : UpdateState
    data class Error(
        val title: String,
        val message: String,
        val retry: Retry
    ) : UpdateState

    sealed interface Retry {
        data object Check : Retry
        data class Download(val release: GitHubRelease) : Retry
    }
}
