package com.ramitsuri.githubandroidupdate.updatechecker.lib.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
sealed interface GitHubRelease {
    @Serializable
    data class Release(
        @SerialName("name")
        val name: String,

        @SerialName("created_at")
        val createdAt: Instant,

        @SerialName("assets")
        val assets: List<Asset>,
    ) : GitHubRelease {
        @Serializable
        data class Asset(
            @SerialName("name")
            val name: String,

            @SerialName("url")
            val apiUrl: String,

            @SerialName("browser_download_url")
            val downloadUrl: String,
        )
    }

    @Serializable
    data class Failure(val message: String) : GitHubRelease
}

