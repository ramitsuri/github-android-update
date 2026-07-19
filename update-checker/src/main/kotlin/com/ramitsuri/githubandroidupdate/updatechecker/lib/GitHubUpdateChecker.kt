package com.ramitsuri.githubandroidupdate.updatechecker.lib

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.ramitsuri.githubandroidupdate.updatechecker.lib.model.GitHubRelease
import com.ramitsuri.githubandroidupdate.updatechecker.lib.network.GithubApi
import java.io.File
import kotlin.time.Instant

class GitHubUpdateChecker(
    private val context: Context,
) {
    private val api = GithubApi()

    suspend fun checkForUpdates(
        owner: String,
        repo: String,
        authToken: String? = null,
        lastCheckedTimestamp: Instant?
    ): GitHubRelease {
        val result = api.getLatestRelease(
            owner = owner,
            repo = repo,
            authToken = authToken
        )
        val release = result.getOrNull()
        if (release == null) {
            Log.e(TAG, "Failed to get latest release")
            return GitHubRelease.Failure("Failed to get latest release")
        }
        val publishedAt = release.createdAt
        return if (lastCheckedTimestamp == null || publishedAt > lastCheckedTimestamp) {
            release
        } else {
            Log.d(TAG, "No new update available")
            GitHubRelease.Failure("No new update available")
        }
    }

    suspend fun downloadAndInstall(release: GitHubRelease.Release): String? {
        val asset = release.assets.find { it.name.endsWith(".apk") }
        if (asset == null) {
            Log.e(TAG, "No APK asset found in release")
            return "No APK asset found in release"
        }

        try {
            File(context.cacheDir, DOWNLOAD_DIR).mkdirs()
            val file = File(context.cacheDir, "$DOWNLOAD_DIR/${asset.name}")
            api.downloadAndSave(url = asset.downloadUrl, toFile = file)
            val uri: Uri =
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download update: ${e.message}")
            return "Failed to download update: ${e.message}"
        }
        return null
    }

    companion object {
        private const val TAG = "GitHubUpdateChecker"
        private const val DOWNLOAD_DIR = "app-download"
    }
}
