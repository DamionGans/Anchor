package com.damiongans.anchor.update

/**
 * Platform-agnostic release metadata model.
 */
data class AppReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val apkDownloadUrl: String,
    val isNewerVersion: Boolean
)

/**
 * Platform-agnostic interface contract for application update management.
 * Decouples network calls, version comparison, and URL launching from platform APIs.
 */
interface IUpdateRepository {
    suspend fun checkForUpdate(
        currentVersionName: String,
        currentReleaseTag: String = ""
    ): AppReleaseInfo?

    fun isVersionNewer(
        remoteTag: String,
        currentVersion: String,
        currentReleaseTag: String = ""
    ): Boolean

    fun openUpdateUrl(downloadUrl: String)
}
