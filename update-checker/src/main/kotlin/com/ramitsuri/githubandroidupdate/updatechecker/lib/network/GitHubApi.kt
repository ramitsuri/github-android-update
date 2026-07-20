package com.ramitsuri.githubandroidupdate.updatechecker.lib.network

import android.util.Log
import com.ramitsuri.githubandroidupdate.updatechecker.lib.model.GitHubRelease
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.serialization.json.Json
import java.io.File

internal class GithubApi() {
    private val apiClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(TAG, message)
                }
            }
            level = LogLevel.ALL
        }
    }

    private val downloadClient = HttpClient {
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(TAG, message)
                }
            }
            level = LogLevel.ALL
        }
    }

    suspend fun getLatestRelease(
        owner: String,
        repo: String,
        authToken: String? = null
    ): Result<GitHubRelease.Release> {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        return try {
            val response = apiClient.get(url) {
                header(HttpHeaders.Accept, "application/vnd.github.v3+json")
                if (!authToken.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $authToken")
                }
            }
            Result.success(response.body())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest release: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun downloadAndSave(
        url: String,
        toFile: File,
        authToken: String? = null
    ) {
        downloadClient.prepareGet(url) {
            header(HttpHeaders.Accept, "application/octet-stream")
            if (!authToken.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }
        }.execute { resp ->
            if (resp.status.isSuccess()) {
                resp.bodyAsChannel().copyAndClose(toFile.writeChannel())
            } else {
                throw Exception("Failed to download file at $url: ${resp.status}")
            }
        }
    }

    companion object {
        private const val TAG = "GithubApi"
    }
}
