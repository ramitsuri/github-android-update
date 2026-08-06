package com.ramitsuri.githubandroidupdate.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ramitsuri.githubandroidupdate.data.model.TrackedRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreManager(context: Context) {
    private val context = context.applicationContext
    private val patKey = stringPreferencesKey("github_pat")
    private val reposKey = stringPreferencesKey("tracked_repos")
    private val selfRepoKey = stringPreferencesKey("self_repo")

    val pat: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[patKey]
    }

    val trackedRepos: Flow<List<TrackedRepo>> = context.dataStore.data.map { preferences ->
        val reposJson = preferences[reposKey] ?: "[]"
        try {
            Json.decodeFromString<List<TrackedRepo>>(reposJson).toMutableList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val selfRepo: Flow<TrackedRepo?> = context.dataStore.data.map { preferences ->
        val selfRepoJson = preferences[selfRepoKey] ?: return@map null
        try {
            Json.decodeFromString<TrackedRepo?>(selfRepoJson)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun savePat(pat: String) {
        context.dataStore.edit { preferences ->
            preferences[patKey] = pat
        }
    }

    suspend fun saveTrackedRepos(repos: List<TrackedRepo>) {
        context.dataStore.edit { preferences ->
            preferences[reposKey] = Json.encodeToString(repos)
        }
    }

    suspend fun addTrackedRepo(owner: String, name: String) {
        context.dataStore.edit { preferences ->
            val currentReposJson = preferences[reposKey] ?: "[]"
            val currentRepos = try {
                Json.decodeFromString<List<TrackedRepo>>(currentReposJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            if (currentRepos.none { it.owner == owner && it.name == name }) {
                currentRepos.add(TrackedRepo(owner, name))
                preferences[reposKey] = Json.encodeToString(currentRepos)
            }
        }
    }

    suspend fun removeTrackedRepo(owner: String, name: String) {
        context.dataStore.edit { preferences ->
            val currentReposJson = preferences[reposKey] ?: "[]"
            val currentRepos = try {
                Json.decodeFromString<List<TrackedRepo>>(currentReposJson).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            currentRepos.removeAll { it.owner == owner && it.name == name }
            preferences[reposKey] = Json.encodeToString(currentRepos)
        }
    }

    suspend fun migrate() {
        val selfRepo = trackedRepos.first().find { it.owner == SELF_OWNER && it.name == SELF_REPO }
        if (selfRepo != null) {
            removeTrackedRepo(owner = SELF_OWNER, name = SELF_REPO)
            context.dataStore.edit { preferences ->
                // Setting hasUpdate = false because there was a bug previously where we recorded it as hasUpdate = true
                // when that wasn't the case.
                preferences[selfRepoKey] = Json.encodeToString(selfRepo.copy(hasUpdate = false))
            }
        }
    }

    companion object {
        const val SELF_OWNER = "ramitsuri"
        const val SELF_REPO = "github-android-update"
    }
}
