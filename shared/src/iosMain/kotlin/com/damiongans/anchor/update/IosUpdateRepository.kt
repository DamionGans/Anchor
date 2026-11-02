package com.damiongans.anchor.update

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * iOS transport for the GitHub Releases update check using `NSURLSession` and
 * `NSJSONSerialization`. Release selection (preferring an `.ipa` asset) and version comparison
 * are shared via [ReleaseVersioning] / [GitHubReleaseConfig].
 */
class IosUpdateRepository : IUpdateRepository {

    override suspend fun checkForUpdate(
        currentVersionName: String,
        currentReleaseTag: String
    ): AppReleaseInfo? {
        for (api in GitHubReleaseConfig.LATEST_RELEASE_APIS) {
            fetchLatestRelease(api, currentVersionName, currentReleaseTag)?.let { return it }
        }
        return null
    }

    private suspend fun fetchLatestRelease(
        apiUrl: String,
        currentVersionName: String,
        currentReleaseTag: String
    ): AppReleaseInfo? = suspendCancellableCoroutine { continuation ->
        val url = NSURL.URLWithString(apiUrl)
        if (url == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest.requestWithURL(url)
        request.setValue("application/vnd.github+json", forHTTPHeaderField = "Accept")
        request.setValue("Anchor-iOS/$currentVersionName", forHTTPHeaderField = "User-Agent")
        request.timeoutInterval = REQUEST_TIMEOUT_SECONDS

        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            val status = (response as? NSHTTPURLResponse)?.statusCode ?: 0L
            val result = if (error == null && data != null && status in 200L..299L) {
                parseRelease(data, currentVersionName, currentReleaseTag)
            } else {
                null
            }
            continuation.resume(result)
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }

    private fun parseRelease(data: NSData, currentVersionName: String, currentReleaseTag: String): AppReleaseInfo? {
        val json = try {
            NSJSONSerialization.JSONObjectWithData(data, 0u, null) as? Map<*, *>
        } catch (_: Exception) {
            null
        } ?: return null

        val assets = (json["assets"] as? List<*>).orEmpty().mapNotNull { entry ->
            val asset = entry as? Map<*, *> ?: return@mapNotNull null
            ReleaseAsset(
                name = asset["name"] as? String ?: "",
                downloadUrl = asset["browser_download_url"] as? String ?: ""
            )
        }

        return ReleaseVersioning.buildReleaseInfo(
            tagName = json["tag_name"] as? String,
            releaseName = json["name"] as? String,
            releaseNotes = json["body"] as? String,
            htmlUrl = json["html_url"] as? String,
            assets = assets,
            assetExtension = ASSET_EXTENSION,
            currentVersionName = currentVersionName,
            currentReleaseTag = currentReleaseTag
        )
    }

    override fun isVersionNewer(
        remoteTag: String,
        currentVersion: String,
        currentReleaseTag: String
    ): Boolean = ReleaseVersioning.isVersionNewer(remoteTag, currentVersion, currentReleaseTag)

    override fun openUpdateUrl(downloadUrl: String) {
        val url = NSURL.URLWithString(downloadUrl) ?: return
        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
        }
    }

    private companion object {
        const val ASSET_EXTENSION = ".ipa"
        const val REQUEST_TIMEOUT_SECONDS = 8.0
    }
}
