package com.damiongans.anchor.update

import com.damiongans.anchor.platform.WindowsArch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Desktop transport for the GitHub Releases update check (`java.net.http`). Release selection
 * (the `.msi` installer for this machine's architecture, see [WindowsArch]) and version comparison
 * are shared via [ReleaseVersioning] / [GitHubReleaseConfig].
 */
class DesktopUpdateRepository(
    private val arch: WindowsArch = WindowsArch.current(),
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
) : IUpdateRepository {

    override suspend fun checkForUpdate(
        currentVersionName: String,
        currentReleaseTag: String
    ): AppReleaseInfo? = withContext(Dispatchers.IO) {
        GitHubReleaseConfig.LATEST_RELEASE_APIS.firstNotNullOfOrNull { api ->
            fetchLatestRelease(api, currentVersionName, currentReleaseTag)
        }
    }

    private fun fetchLatestRelease(apiUrl: String, currentVersionName: String, currentReleaseTag: String): AppReleaseInfo? =
        try {
            val request = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Anchor-Desktop/$currentVersionName")
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) {
                parseRelease(response.body(), currentVersionName, currentReleaseTag, arch)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

    override fun isVersionNewer(remoteTag: String, currentVersion: String, currentReleaseTag: String): Boolean =
        ReleaseVersioning.isVersionNewer(remoteTag, currentVersion, currentReleaseTag)

    override fun openUpdateUrl(downloadUrl: String) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(downloadUrl))
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private val TIMEOUT: Duration = Duration.ofSeconds(8)
        const val ASSET_EXTENSION = ".msi"

        /** Parses a GitHub "latest release" JSON body; `null` for malformed or tagless releases. */
        fun parseRelease(
            body: String,
            currentVersionName: String,
            currentReleaseTag: String,
            arch: WindowsArch
        ): AppReleaseInfo? {
            val json = try {
                Json.parseToJsonElement(body) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return null

            val assets = (json["assets"] as? JsonArray).orEmpty().mapNotNull { entry ->
                val asset = entry as? JsonObject ?: return@mapNotNull null
                ReleaseAsset(
                    name = asset["name"].stringOrNull().orEmpty(),
                    downloadUrl = asset["browser_download_url"].stringOrNull().orEmpty()
                )
            }

            return ReleaseVersioning.buildReleaseInfo(
                tagName = json["tag_name"].stringOrNull(),
                releaseName = json["name"].stringOrNull(),
                releaseNotes = json["body"].stringOrNull(),
                htmlUrl = json["html_url"].stringOrNull(),
                assets = assets,
                assetExtension = ASSET_EXTENSION,
                currentVersionName = currentVersionName,
                currentReleaseTag = currentReleaseTag,
                preferredAssetMarker = arch.assetMarker
            )
        }

        private fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    }
}
