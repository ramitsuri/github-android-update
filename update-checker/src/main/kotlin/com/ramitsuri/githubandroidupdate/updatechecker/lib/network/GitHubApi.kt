package com.ramitsuri.githubandroidupdate.updatechecker.lib.network

import android.util.Log
import com.ramitsuri.githubandroidupdate.updatechecker.lib.model.GitHubRelease
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import java.io.File

internal class GithubApi {
    suspend fun getLatestRelease(
        owner: String,
        repo: String,
        authToken: String? = null
    ): Result<GitHubRelease.Release> {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        return try {
            Result.success(client(authToken).get(url).body())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest release: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun downloadAndSave(url: String, toFile: File) {
        client().prepareGet(url).execute { resp ->
            resp.bodyAsChannel().copyAndClose(toFile.writeChannel())
        }
    }

    private fun client(authToken: String? = null) = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        defaultRequest {
            url("https://api.github.com/")
            header(HttpHeaders.Accept, "application/vnd.github.v3+json")
            if (!authToken.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "token $authToken")
            }
        }
    }

    companion object {
        private const val TAG = "GithubApi"
    }
}