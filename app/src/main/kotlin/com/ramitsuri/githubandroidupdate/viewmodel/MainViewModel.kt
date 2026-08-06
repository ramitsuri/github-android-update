package com.ramitsuri.githubandroidupdate.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ramitsuri.githubandroidupdate.data.DataStoreManager
import com.ramitsuri.githubandroidupdate.data.model.TrackedRepo
import com.ramitsuri.githubandroidupdate.updatechecker.lib.GitHubUpdateChecker
import com.ramitsuri.githubandroidupdate.updatechecker.lib.model.GitHubRelease
import com.ramitsuri.githubandroidupdate.worker.UpdateWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.time.Instant

data class MainUiState(
    val pat: String? = null,
    val trackedRepos: List<TrackedRepo> = emptyList(),
    val selfRepo: TrackedRepo? = null,
    val downloadProgress: Map<String, Float> = emptyMap(),
    val isLoaded: Boolean = false,
)

class MainViewModel(private val application: Application) : AndroidViewModel(application) {
    private val dataStoreManager = DataStoreManager(application.applicationContext)
    private val updateChecker = GitHubUpdateChecker(application.applicationContext)

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())

    init {
        viewModelScope.launch {
            dataStoreManager.migrate()
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        dataStoreManager.pat,
        dataStoreManager.trackedRepos,
        dataStoreManager.selfRepo,
        _downloadProgress,
    ) { pat, trackedRepos, selfRepo, downloadProgress ->
        MainUiState(
            pat = pat,
            selfRepo = selfRepo,
            trackedRepos = trackedRepos,
            downloadProgress = downloadProgress,
            isLoaded = true
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MainUiState(isLoaded = false)
    )

    init {
        scheduleWorker()
    }

    fun savePat(pat: String) {
        viewModelScope.launch {
            dataStoreManager.savePat(pat)
        }
    }

    fun addRepo(owner: String, name: String) {
        viewModelScope.launch {
            dataStoreManager.addTrackedRepo(owner, name)
            checkForUpdates(owner, name)
        }
    }

    fun removeRepo(repo: TrackedRepo) {
        viewModelScope.launch {
            dataStoreManager.removeTrackedRepo(repo.owner, repo.name)
        }
    }

    fun checkForUpdates(owner: String, name: String) {
        viewModelScope.launch {
            val pat = uiState.value.pat
            val currentRepos = uiState.value.trackedRepos.toMutableList()
            val repoIndex = currentRepos.indexOfFirst { it.owner == owner && it.name == name }
            if (repoIndex == -1) return@launch

            val repo = currentRepos[repoIndex]
            val lastTimestamp = repo.latestReleaseTimestamp?.let { Instant.fromEpochMilliseconds(it) }
            val release = updateChecker.checkForUpdates(
                owner = repo.owner,
                repo = repo.name,
                authToken = pat,
                lastCheckedTimestamp = lastTimestamp,
            )

            if (release is GitHubRelease.Release) {
                val hasUpdate = lastTimestamp != null && release.createdAt > lastTimestamp
                currentRepos[repoIndex] = repo.copy(
                    latestReleaseVersion = release.name,
                    latestReleaseTimestamp = release.createdAt.toEpochMilliseconds(),
                    hasUpdate = hasUpdate,
                )
                dataStoreManager.saveTrackedRepos(currentRepos)
            }
        }
    }

    fun downloadAndInstall(repo: TrackedRepo) {
        viewModelScope.launch {
            val patValue = uiState.value.pat
            val release = updateChecker.checkForUpdates(repo.owner, repo.name, patValue, null)
            if (release is GitHubRelease.Release) {
                try {
                    updateChecker.downloadAndInstall(release, patValue).collect { progress ->
                        _downloadProgress.update { it + (repo.fullName to progress) }
                    }
                } catch (e: Exception) {
                    // Handle error if needed
                } finally {
                    _downloadProgress.update { it - repo.fullName }
                }

                // Clear update flag after install attempt
                val currentRepos = uiState.value.trackedRepos.toMutableList()
                val repoIndex =
                    currentRepos.indexOfFirst { it.owner == repo.owner && it.name == repo.name }
                if (repoIndex != -1) {
                    currentRepos[repoIndex] = currentRepos[repoIndex].copy(hasUpdate = false)
                    dataStoreManager.saveTrackedRepos(currentRepos)
                }
            }
        }
    }

    private fun scheduleWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(application).enqueueUniquePeriodicWork(
            UpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
