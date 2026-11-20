package com.pulscen.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File

/**
 * Checks that a downloaded APK belongs to «Пульс цен» and is signed with the
 * same certificate as the installed app.
 */
object ApkVerifier {

    fun isSameApp(context: Context, file: File): Boolean {
        val packageManager = context.packageManager
        val archive = packageInfo(packageManager, file) ?: return false
        if (archive.packageName != context.packageName) return false
        val current = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
        } catch (e: Exception) {
            return false
        }
        return sameSigners(signers(archive), signers(current))
    }

    fun versionName(context: Context, file: File): String? =
        packageInfo(context.packageManager, file)?.versionName

    private fun packageInfo(packageManager: PackageManager, file: File): PackageInfo? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    PackageManager.GET_SIGNATURES
                )
            }
        } catch (e: Exception) {
            null
        }

    private fun signers(info: PackageInfo): Array<Signature>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }

    private fun sameSigners(
        first: Array<Signature>?,
        second: Array<Signature>?
    ): Boolean {
        if (first.isNullOrEmpty() || second.isNullOrEmpty()) return false
        return first.any { a ->
            second.any { b -> a.toByteArray().contentEquals(b.toByteArray()) }
        }
    }
}
