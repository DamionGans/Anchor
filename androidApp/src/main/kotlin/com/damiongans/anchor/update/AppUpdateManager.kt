package com.damiongans.anchor.update

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Production Android implementation of [IUpdateRepository] delegating to [AppUpdateManager].
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
        return ReleaseVersioning.isVersionNewer(remoteTag, currentVersion, currentReleaseTag)
    }

    override fun openUpdateUrl(downloadUrl: String) {
        val ctx = appContext ?: return
        AppUpdateManager.openUpdateUrl(ctx, downloadUrl)
    }
}

/**
 * Android transport for the GitHub Releases update check (`DamionGans/Anchor` with automatic
 * fallback to `DamionGans/PulseMetronome`) using `HttpURLConnection` and `org.json` (zero
 * third-party networking footprint). Release selection and version comparison are shared via
 * [ReleaseVersioning] / [GitHubReleaseConfig].
 */
object AppUpdateManager {

    const val GITHUB_REPO = GitHubReleaseConfig.GITHUB_REPO
    const val RELEASES_PAGE_URL = GitHubReleaseConfig.RELEASES_PAGE_URL
    private const val ASSET_EXTENSION = ".apk"

    suspend fun checkForUpdate(
        currentVersionName: String,
        currentReleaseTag: String = ""
    ): AppReleaseInfo? = withContext(Dispatchers.IO) {
        GitHubReleaseConfig.LATEST_RELEASE_APIS.firstNotNullOfOrNull { api ->
            fetchLatestReleaseFromApi(api, currentVersionName, currentReleaseTag)
        }
    }

    private fun fetchLatestReleaseFromApi(
        apiUrl: String,
        currentVersionName: String,
        currentReleaseTag: String
    ): AppReleaseInfo? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
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
            val assets = mutableListOf<ReleaseAsset>()
            json.optJSONArray("assets")?.let { array ->
                for (i in 0 until array.length()) {
                    val asset = array.optJSONObject(i) ?: continue
                    assets.add(
                        ReleaseAsset(
                            name = asset.optString("name", ""),
                            downloadUrl = asset.optString("browser_download_url", "")
                        )
                    )
                }
            }

            ReleaseVersioning.buildReleaseInfo(
                tagName = json.optString("tag_name", ""),
                releaseName = json.optString("name", ""),
                releaseNotes = if (json.has("body") && !json.isNull("body")) json.optString("body") else null,
                htmlUrl = json.optString("html_url", ""),
                assets = assets,
                assetExtension = ASSET_EXTENSION,
                currentVersionName = currentVersionName,
                currentReleaseTag = currentReleaseTag
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Compares a remote GitHub tag (e.g. "v1.4.0") with the installed versionName (e.g. "1.4.0").
     */
    fun isVersionNewer(
        remoteTag: String,
        currentVersion: String,
        currentReleaseTag: String = ""
    ): Boolean = ReleaseVersioning.isVersionNewer(remoteTag, currentVersion, currentReleaseTag)

    /**
     * Opens the update URL using a zero-permission ACTION_VIEW Intent.
     */
    fun openUpdateUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
