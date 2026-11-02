package com.damiongans.anchor.platform

/**
 * Windows CPU architectures supported by the future desktop app. Both run the same JVM build;
 * installers are produced per architecture (see docs/windows.md).
 */
enum class WindowsArch(
    /** Fragment in release asset names, e.g. `Anchor-v1.6.0-windows-arm64.msi`. */
    val assetMarker: String
) {
    X64("windows-x64"),
    ARM64("windows-arm64");

    companion object {
        /** Maps a JVM `os.arch` value (`amd64`, `x86_64`, `aarch64`, `arm64`) to an architecture. */
        fun fromOsArch(osArch: String?): WindowsArch = when (osArch?.trim()?.lowercase()) {
            "aarch64", "arm64" -> ARM64
            else -> X64
        }

        fun current(): WindowsArch = fromOsArch(System.getProperty("os.arch"))

        fun isWindows(osName: String? = System.getProperty("os.name")): Boolean =
            osName?.startsWith("Windows", ignoreCase = true) == true
    }
}
