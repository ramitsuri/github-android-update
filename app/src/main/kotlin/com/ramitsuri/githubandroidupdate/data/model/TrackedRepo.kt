package com.ramitsuri.githubandroidupdate.data.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackedRepo(
    val owner: String,
    val name: String,
    val latestReleaseVersion: String? = null,
    val latestReleaseTimestamp: Long? = null,
    val hasUpdate: Boolean = false,
    val hasWearUpdate: Boolean = false
) {
    val fullName: String get() = "$owner/$name"
}
