package com.damiongans.anchor.desktop

import com.damiongans.anchor.audio.DesktopAudioEngine
import com.damiongans.anchor.haptics.NoOpHapticsManager
import com.damiongans.anchor.playback.NoOpPlaybackSession
import com.damiongans.anchor.repository.KeyValueSettingsRepository
import com.damiongans.anchor.repository.PreferencesKeyValueStore
import com.damiongans.anchor.update.AppVersionInfo
import com.damiongans.anchor.update.DesktopUpdateRepository
import com.damiongans.anchor.update.DistributionChannel
import com.damiongans.anchor.viewmodel.MetronomeController
import kotlinx.coroutines.CoroutineScope

/**
 * Composition root for the future Windows (x64 / arm64) desktop app, analogous to `AnchorIosApp`.
 *
 * Wires the desktop platform implementations into the shared [MetronomeController]. The app
 * (Compose Multiplatform Desktop, see docs/windows.md) supplies a main-thread [scope], e.g. one
 * on `Dispatchers.Main` from `kotlinx-coroutines-swing`.
 *
 * Not yet provided on desktop: haptics (no hardware) and system media controls (Windows System
 * Media Transport Controls need native interop); both use no-op implementations.
 */
object AnchorDesktop {

    fun createController(
        scope: CoroutineScope,
        versionName: String,
        channel: DistributionChannel = DistributionChannel.GITHUB
    ): MetronomeController = MetronomeController(
        audioEngine = DesktopAudioEngine(),
        hapticsManager = NoOpHapticsManager(),
        settingsRepository = KeyValueSettingsRepository(PreferencesKeyValueStore()),
        updateRepository = DesktopUpdateRepository(),
        appVersion = AppVersionInfo(versionName = versionName, channel = channel),
        scope = scope,
        playbackSession = NoOpPlaybackSession()
    )
}
