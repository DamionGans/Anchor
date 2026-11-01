package com.damiongans.pulse.update

import android.content.Context

/**
 * Interface contract for application update management.
 * Decouples network calls and version checks for clean testing.
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

    fun openUpdateUrl(context: Context, downloadUrl: String)
}

/**
 * Production implementation of IUpdateRepository delegating to AppUpdateManager.
 */
class GitHubUpdateRepository : IUpdateRepository {

    override suspend fun checkForUpdate(
        currentVersionName: String,
        currentReleaseTag: String
    ): AppReleaseInfo? {
        return AppUpdateManager.checkForUpdate(currentVersionName, currentReleaseTag)
    }

    override fun isVersionNewer(
        remoteTag: String,
        currentVersion: String,
        currentReleaseTag: String
    ): Boolean {
        return AppUpdateManager.isVersionNewer(remoteTag, currentVersion, currentReleaseTag)
    }

    override fun openUpdateUrl(context: Context, downloadUrl: String) {
        AppUpdateManager.openUpdateUrl(context, downloadUrl)
    }
}
