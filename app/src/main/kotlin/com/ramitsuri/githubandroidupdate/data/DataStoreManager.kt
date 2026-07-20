package com.ramitsuri.githubandroidupdate.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ramitsuri.githubandroidupdate.data.model.TrackedRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreManager(context: Context) {
    private val context = context.applicationContext
    private val patKey = stringPreferencesKey("github_pat")
    private val reposKey = stringPreferencesKey("tracked_repos")

    val pat: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[patKey]
    }

    val trackedRepos: Flow<List<TrackedRepo>> = context.dataStore.data.map { preferences ->
        val reposJson = preferences[reposKey] ?: "[]"
        try {
            Json.decodeFromString<List<TrackedRepo>>(reposJson)
        } catch (e: Exception) {
            emptyList()
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
}
