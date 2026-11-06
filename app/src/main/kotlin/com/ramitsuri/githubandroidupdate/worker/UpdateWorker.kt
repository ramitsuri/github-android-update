package com.ramitsuri.githubandroidupdate.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ramitsuri.githubandroidupdate.data.DataStoreManager
import com.ramitsuri.githubandroidupdate.updatechecker.lib.GitHubUpdateChecker
import kotlinx.coroutines.flow.first

class UpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dataStoreManager = DataStoreManager(context)
        val pat = dataStoreManager.pat.first()
        val repos = dataStoreManager.trackedRepos.first()
        val updateChecker = GitHubUpdateChecker(context)

        val updatedRepos = updateChecker.checkForUpdates(repos, pat)
        dataStoreManager.saveTrackedRepos(updatedRepos)

        updateChecker.cleanupOldDownloads()

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "UpdateWorker"
    }
}
