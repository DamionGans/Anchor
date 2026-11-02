package com.damiongans.anchor.update

/**
 * Shared GitHub Releases configuration. `DamionGans/Anchor` is checked first with an automatic
 * fallback to the legacy `DamionGans/PulseMetronome` repository.
 */
object GitHubReleaseConfig {
    const val GITHUB_REPO = "DamionGans/Anchor"
    const val LEGACY_GITHUB_REPO = "DamionGans/PulseMetronome"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    const val LEGACY_LATEST_RELEASE_API = "https://api.github.com/repos/$LEGACY_GITHUB_REPO/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_REPO/releases"

    /** Release API endpoints in lookup order. */
    val LATEST_RELEASE_APIS = listOf(LATEST_RELEASE_API, LEGACY_LATEST_RELEASE_API)

    const val DEFAULT_RELEASE_NOTES = "Bug fixes and performance improvements."
}

/**
 * A downloadable asset attached to a GitHub release.
 */
data class ReleaseAsset(val name: String, val downloadUrl: String)

/**
 * Platform-agnostic release parsing result builder and semantic version comparison.
 * Platforms only perform the HTTP request and JSON field extraction.
 */
object ReleaseVersioning {

    /**
     * Builds an [AppReleaseInfo] from raw GitHub release fields.
     *
     * @param assetExtension Platform package extension to prefer (e.g. ".apk" or ".ipa").
     * @return `null` when the release has no tag.
     */
    fun buildReleaseInfo(
        tagName: String?,
        releaseName: String?,
        releaseNotes: String?,
        htmlUrl: String?,
        assets: List<ReleaseAsset>,
        assetExtension: String,
        currentVersionName: String,
        currentReleaseTag: String
    ): AppReleaseInfo? {
        val tag = tagName?.trim().orEmpty()
        if (tag.isEmpty()) return null

        val pageUrl = htmlUrl?.takeIf { it.isNotBlank() } ?: GitHubReleaseConfig.RELEASES_PAGE_URL
        val assetUrl = assets.firstOrNull {
            it.name.endsWith(assetExtension, ignoreCase = true) && it.downloadUrl.isNotEmpty()
        }?.downloadUrl ?: pageUrl

        return AppReleaseInfo(
            tagName = tag,
            releaseName = releaseName?.takeIf { it.isNotBlank() } ?: tag,
            releaseNotes = releaseNotes ?: GitHubReleaseConfig.DEFAULT_RELEASE_NOTES,
            releasePageUrl = pageUrl,
            downloadUrl = assetUrl,
            isNewerVersion = isVersionNewer(tag, currentVersionName, currentReleaseTag)
        )
    }

    /**
     * Compares a remote GitHub tag (e.g. "v1.3.0") with the installed version name (e.g. "1.3.0").
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
        val remoteParts = cleanRemote.substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }

        if (remoteParts.isEmpty() || currentParts.isEmpty()) {
            return false
        }

        // Ignore legacy date-stamped 3rd component (e.g. 1.0.20261101) when comparing against 1.2.x+
        val normalizedRemote = remoteParts.mapIndexed { idx, value -> if (idx >= 2 && value > 20000000) 0 else value }
        val normalizedCurrent = currentParts.mapIndexed { idx, value -> if (idx >= 2 && value > 20000000) 0 else value }

        val maxLen = maxOf(normalizedRemote.size, normalizedCurrent.size)
        for (i in 0 until maxLen) {
            val r = normalizedRemote.getOrElse(i) { 0 }
            val c = normalizedCurrent.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}

/**
 * Installed application version, supplied by each platform (Android `BuildConfig`,
 * iOS `CFBundleShortVersionString`).
 */
data class AppVersionInfo(
    val versionName: String,
    val releaseTag: String = "v$versionName"
)
