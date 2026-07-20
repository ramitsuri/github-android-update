package com.ramitsuri.githubandroidupdate.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ramitsuri.githubandroidupdate.data.DataStoreManager
import com.ramitsuri.githubandroidupdate.updatechecker.lib.GitHubUpdateChecker
import com.ramitsuri.githubandroidupdate.updatechecker.lib.model.GitHubRelease
import kotlinx.coroutines.flow.first
import java.io.File
import kotlin.time.Instant

class UpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dataStoreManager = DataStoreManager(context)
        val pat = dataStoreManager.pat.first()
        val repos = dataStoreManager.trackedRepos.first()
        val updateChecker = GitHubUpdateChecker(context)

        val updatedRepos = repos.map { repo ->
            val lastTimestamp = repo.latestReleaseTimestamp?.let { Instant.fromEpochMilliseconds(it) }
            val release = updateChecker.checkForUpdates(
                owner = repo.owner,
                repo = repo.name,
                authToken = pat,
                lastCheckedTimestamp = lastTimestamp
            )

            if (release is GitHubRelease.Release) {
                repo.copy(
                    latestReleaseVersion = release.name,
                    latestReleaseTimestamp = release.createdAt.toEpochMilliseconds(),
                    hasUpdate = true
                )
            } else {
                repo
            }
        }

        dataStoreManager.saveTrackedRepos(updatedRepos)
        
        cleanupOldDownloads()

        return Result.success()
    }

    private fun cleanupOldDownloads() {
        val downloadDir = File(context.cacheDir, "app-download")
        if (downloadDir.exists() && downloadDir.isDirectory) {
            downloadDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
        }
    }

    companion object {
        const val WORK_NAME = "UpdateWorker"
    }
}
