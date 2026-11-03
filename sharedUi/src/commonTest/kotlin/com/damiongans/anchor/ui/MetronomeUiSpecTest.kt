package com.damiongans.anchor.ui

import androidx.compose.ui.unit.dp
import com.damiongans.anchor.audio.IAudioEngine
import com.damiongans.anchor.haptics.NoOpHapticsManager
import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputDevice
import com.damiongans.anchor.model.AudioOutputInfo
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.repository.InMemoryKeyValueStore
import com.damiongans.anchor.repository.KeyValueSettingsRepository
import com.damiongans.anchor.ui.dialogs.formatSignedMs
import com.damiongans.anchor.ui.dialogs.formatTwoDecimals
import com.damiongans.anchor.ui.dialogs.offsetDirectionLabel
import com.damiongans.anchor.ui.dialogs.resizeAccents
import com.damiongans.anchor.update.AppReleaseInfo
import com.damiongans.anchor.update.AppVersionInfo
import com.damiongans.anchor.update.DistributionChannel
import com.damiongans.anchor.update.IUpdateRepository
import com.damiongans.anchor.viewmodel.MetronomeController
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetronomeUiSpecTest {

    private fun createController(channel: DistributionChannel = DistributionChannel.GITHUB): MetronomeController {
        val store = InMemoryKeyValueStore()
        return MetronomeController(
            audioEngine = NoOpTestAudioEngine(),
            hapticsManager = NoOpHapticsManager(),
            settingsRepository = KeyValueSettingsRepository(store),
            updateRepository = NoOpTestUpdateRepository(),
            appVersion = AppVersionInfo(versionName = "1.7.0", channel = channel),
            scope = TestScope(),
            clockMs = { 0L },
            autoCheckForUpdates = false
        )
    }

    /** SPEC-PROD-002 */
    @Test
    fun distributionChannels_exposeCorrectSelfUpdateAndStoreBehavior() {
        val githubCtrl = createController(DistributionChannel.GITHUB)
        assertTrue(githubCtrl.uiState.value.canCheckForUpdates)
        assertNull(githubCtrl.uiState.value.updateStoreName)

        val playCtrl = createController(DistributionChannel.PLAY_STORE)
        assertFalse(playCtrl.uiState.value.canCheckForUpdates)
        assertEquals("Google Play", playCtrl.uiState.value.updateStoreName)

        val fdroidCtrl = createController(DistributionChannel.FDROID)
        assertFalse(fdroidCtrl.uiState.value.canCheckForUpdates)
        assertEquals("F-Droid", fdroidCtrl.uiState.value.updateStoreName)

        val appStoreCtrl = createController(DistributionChannel.APP_STORE)
        assertFalse(appStoreCtrl.uiState.value.canCheckForUpdates)
        assertEquals("the App Store", appStoreCtrl.uiState.value.updateStoreName)
    }

    /** SPEC-UI-001 */
    @Test
    fun isWideStudioLayout_selectsTwoPaneSplitOnlyWhenWideAndTallEnough() {
        // Compact phone portrait -> single column
        assertFalse(isWideStudioLayout(screenWidth = 390.dp, screenHeight = 844.dp))
        // Compact phone landscape (short height < 480.dp) -> single column so right pane does not clip
        assertFalse(isWideStudioLayout(screenWidth = 844.dp, screenHeight = 390.dp))
        // Tablet / desktop studio window (900x620 dp) -> wide two-pane layout
        assertTrue(isWideStudioLayout(screenWidth = 900.dp, screenHeight = 620.dp))
        // 680x480 boundary -> wide two-pane layout
        assertTrue(isWideStudioLayout(screenWidth = 680.dp, screenHeight = 480.dp))
    }

    /** SPEC-UI-002 */
    @Test
    fun setlistBarState_tracksActivePlaylistSongPositionAndModifiedFlag() {
        val controller = createController()
        assertNull(controller.uiState.value.activePlaylist)
        assertNull(controller.uiState.value.activeSong)

        val s1 = controller.createSong("Opener", 110, 4, Subdivision.QUARTER, emptyList())
        val s2 = controller.createSong("Encore", 150, 6, Subdivision.EIGHTH, emptyList())
        val playlist = controller.createPlaylist("Friday Gig")
        controller.addSongToPlaylist(playlist.id, s1.id)
        controller.addSongToPlaylist(playlist.id, s2.id)

        controller.selectPlaylist(playlist.id)
        assertEquals("Friday Gig", controller.uiState.value.activePlaylist?.name)
        assertEquals(0, controller.uiState.value.activeSongIndex)
        assertEquals("Opener", controller.uiState.value.activeSong?.name)
        assertFalse(controller.uiState.value.isActiveSongModified)

        controller.adjustBpm(2)
        assertTrue(controller.uiState.value.isActiveSongModified)

        controller.nextSong()
        assertEquals(1, controller.uiState.value.activeSongIndex)
        assertEquals("Encore", controller.uiState.value.activeSong?.name)
        assertFalse(controller.uiState.value.isActiveSongModified)
    }

    /** SPEC-UI-003 */
    @Test
    fun dialogAndSheetFormattingHelpers_formatAccentsPitchAndOffsetsCleanly() {
        assertEquals("1.00", formatTwoDecimals(1.0f))
        assertEquals("1.25", formatTwoDecimals(1.25f))
        assertEquals("+15ms", formatSignedMs(15))
        assertEquals("-20ms", formatSignedMs(-20))
        assertEquals("0ms", formatSignedMs(0))
        assertTrue(offsetDirectionLabel(-10).contains("earlier"))
        assertTrue(offsetDirectionLabel(10).contains("later"))
        assertTrue(offsetDirectionLabel(0).contains("locked"))

        val resized = resizeAccents(listOf(BeatAccent.MUTE), 3)
        assertEquals(listOf(BeatAccent.MUTE, BeatAccent.REGULAR, BeatAccent.REGULAR), resized)
    }
}

private class NoOpTestAudioEngine : IAudioEngine {
    override var bpm: Double = 120.0
    override var totalBeats: Int = 4
    override var currentSubdivision: Subdivision = Subdivision.QUARTER
    override var accents: List<BeatAccent> = emptyList()
    override var currentSoundSet: SoundSet = SoundSet.WOODBLOCK
    override var pitchMultiplier: Float = 1.0f
    override var visualDelayMs: Int = 0
    override var vibrationDelayMs: Int = 0
    override var manualBluetoothCompensationMs: Int = 0
    override var currentAudioInfo: AudioOutputInfo = AudioOutputInfo(
        deviceType = AudioDeviceType.SPEAKER,
        selectedDeviceId = -1,
        availableDevices = listOf(AudioOutputDevice(-1, AudioDeviceType.SPEAKER, "Built-in Speaker"))
    )
    override var onBeatTick: ((Int, Boolean, Int) -> Unit)? = null
    override var onVibrationTick: ((Int, Boolean) -> Unit)? = null
    override var onPhaseUpdate: ((Int, Float) -> Unit)? = null
    override var onBarCompleted: (() -> Unit)? = null
    override var onAudioDeviceChanged: ((AudioOutputInfo) -> Unit)? = null
    override fun start() {}
    override fun stop() {}
    override fun release() {}
    override fun detectCurrentAudioDevice() {}
    override fun selectAudioOutputDevice(deviceId: Int?) {}
}

private class NoOpTestUpdateRepository : IUpdateRepository {
    override suspend fun checkForUpdate(currentVersionName: String, currentReleaseTag: String): AppReleaseInfo? = null
    override fun isVersionNewer(remoteTag: String, currentVersion: String, currentReleaseTag: String): Boolean = false
    override fun openUpdateUrl(downloadUrl: String) {}
}
