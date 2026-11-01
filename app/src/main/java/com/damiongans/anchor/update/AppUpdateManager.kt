package com.damiongans.anchor.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Production implementation of [IUpdateRepository] delegating to [AppUpdateManager].
 */
class GitHubUpdateRepository(
    private val appContext: Context? = null
) : IUpdateRepository {

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

    override fun openUpdateUrl(downloadUrl: String) {
        val ctx = appContext ?: return
        AppUpdateManager.openUpdateUrl(ctx, downloadUrl)
    }
}

/**
 * Checks for updates directly from GitHub Releases (`DamionGans/Anchor` with automatic fallback
 * to `DamionGans/PulseMetronome` during repository rename transition) using Android's standard
 * HttpURLConnection (zero third-party networking library footprint).
 */
object AppUpdateManager {

    const val GITHUB_REPO = "DamionGans/Anchor"
    private const val LEGACY_GITHUB_REPO = "DamionGans/PulseMetronome"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val LEGACY_LATEST_RELEASE_API = "https://api.github.com/repos/$LEGACY_GITHUB_REPO/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_REPO/releases"

    suspend fun checkForUpdate(
        currentVersionName: String,
        currentReleaseTag: String = ""
    ): AppReleaseInfo? = withContext(Dispatchers.IO) {
        fetchLatestReleaseFromApi(LATEST_RELEASE_API, currentVersionName, currentReleaseTag)
            ?: fetchLatestReleaseFromApi(LEGACY_LATEST_RELEASE_API, currentVersionName, currentReleaseTag)
    }

    private fun fetchLatestReleaseFromApi(
        apiUrl: String,
        currentVersionName: String,
        currentReleaseTag: String
    ): AppReleaseInfo? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(apiUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Anchor-Android/$currentVersionName")
            }

            if (connection.responseCode !in 200..299) return null
            val bodyString = connection.inputStream.bufferedReader().use { it.readText() }
            if (bodyString.isBlank()) return null

            val json = JSONObject(bodyString)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isEmpty()) return null

            val releaseName = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "Bug fixes and performance improvements.")
            val htmlUrl = json.optString("html_url", RELEASES_PAGE_URL)

            // Find the first .apk asset in the release
            var apkUrl = htmlUrl
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotEmpty()) {
                        apkUrl = downloadUrl
                        break
                    }
                }
            }

            val isNewer = isVersionNewer(
                remoteTag = tagName,
                currentVersion = currentVersionName,
                currentReleaseTag = currentReleaseTag
            )

            AppReleaseInfo(
                tagName = tagName,
                releaseName = releaseName,
                releaseNotes = releaseNotes,
                releasePageUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                isNewerVersion = isNewer
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Compares remote GitHub tag (e.g. "v1.3.0") with current installed versionName (e.g. "1.3.0").
     */
    fun isVersionNewer(
        remoteTag: String,
        currentVersion: String,
        currentReleaseTag: String = ""
    ): Boolean {
        val trimmedRemote = remoteTag.trim()
        if (trimmedRemote.isEmpty()) return false

        // 1. Exact release tag match -> already installed
        if (currentReleaseTag.isNotBlank() && trimmedRemote.equals(currentReleaseTag.trim(), ignoreCase = true)) {
            return false
        }

        val cleanRemote = trimmedRemote.removePrefix("v").removePrefix("V").trim()
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V").trim()

        // 2. Exact version name match -> already installed
        if (cleanRemote.equals(cleanCurrent, ignoreCase = true)) return false

        // 3. Extract leading semantic version before any hyphen metadata
        val remoteSemver = cleanRemote.substringBefore("-")
        val currentSemver = cleanCurrent.substringBefore("-")

        val remoteParts = remoteSemver.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentSemver.split(".").mapNotNull { it.toIntOrNull() }

        if (remoteParts.isEmpty() || currentParts.isEmpty()) {
            return false
        }

        // Ignore legacy date-stamped 3rd component (e.g. 1.0.20261101) when comparing against 1.2.x+
        val normalizedRemote = remoteParts.mapIndexed { idx, value ->
            if (idx >= 2 && value > 20000000) 0 else value
        }
        val normalizedCurrent = currentParts.mapIndexed { idx, value ->
            if (idx >= 2 && value > 20000000) 0 else value
        }

        val maxLen = maxOf(normalizedRemote.size, normalizedCurrent.size)
        for (i in 0 until maxLen) {
            val r = normalizedRemote.getOrElse(i) { 0 }
            val c = normalizedCurrent.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Opens the update URL using a zero-permission ACTION_VIEW Intent.
     */
    fun openUpdateUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
