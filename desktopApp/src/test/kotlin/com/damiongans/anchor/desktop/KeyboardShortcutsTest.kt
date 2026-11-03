package com.damiongans.anchor.desktop

import androidx.compose.ui.input.key.Key
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
import com.damiongans.anchor.update.AppReleaseInfo
import com.damiongans.anchor.update.AppVersionInfo
import com.damiongans.anchor.update.IUpdateRepository
import com.damiongans.anchor.viewmodel.MetronomeController
import kotlinx.coroutines.test.TestScope
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyboardShortcutsTest {

    private lateinit var controller: MetronomeController
    private var currentClockMs: Long = 1000L

    @BeforeTest
    fun setUp() {
        val store = InMemoryKeyValueStore()
        val settings = KeyValueSettingsRepository(store)
        controller = MetronomeController(
            audioEngine = StubAudioEngine(),
            hapticsManager = NoOpHapticsManager(),
            settingsRepository = settings,
            updateRepository = StubUpdateRepository(),
            appVersion = AppVersionInfo("1.7.2"),
            scope = TestScope(),
            clockMs = { currentClockMs },
            autoCheckForUpdates = false
        )
    }

    /** SPEC-UI-004 */
    @Test
    fun spacebar_togglesPlayPause() {
        assertFalse(controller.uiState.value.isPlaying)
        assertTrue(KeyboardShortcuts.handleKey(Key.Spacebar, controller = controller))
        assertTrue(controller.uiState.value.isPlaying)
        assertTrue(KeyboardShortcuts.handleKey(Key.Spacebar, controller = controller))
        assertFalse(controller.uiState.value.isPlaying)
    }

    /** SPEC-UI-004 */
    @Test
    fun arrowAndPlusMinusKeys_adjustBpmBy1Or5WithShift() {
        assertEquals(120, controller.uiState.value.bpm)
        assertTrue(KeyboardShortcuts.handleKey(Key.DirectionUp, controller = controller))
        assertEquals(121, controller.uiState.value.bpm)
        assertTrue(KeyboardShortcuts.handleKey(Key.DirectionUp, isShiftPressed = true, controller = controller))
        assertEquals(126, controller.uiState.value.bpm)
        assertTrue(KeyboardShortcuts.handleKey(Key.DirectionDown, controller = controller))
        assertEquals(125, controller.uiState.value.bpm)
        assertTrue(KeyboardShortcuts.handleKey(Key.DirectionDown, isShiftPressed = true, controller = controller))
        assertEquals(120, controller.uiState.value.bpm)
    }

    /** SPEC-UI-004 */
    @Test
    fun tKey_triggersTapTempo() {
        assertTrue(KeyboardShortcuts.handleKey(Key.T, controller = controller))
        currentClockMs += 600L // 100 BPM
        assertTrue(KeyboardShortcuts.handleKey(Key.T, controller = controller))
        assertEquals(100, controller.uiState.value.bpm)
    }

    /** SPEC-UI-004 */
    @Test
    fun pageUpDownAndBrackets_stepThroughActivePlaylist() {
        val s1 = controller.createSong("Song 1", 100, 4, Subdivision.QUARTER, emptyList())
        val s2 = controller.createSong("Song 2", 140, 3, Subdivision.EIGHTH, emptyList())
        val playlist = controller.createPlaylist("Set 1")
        controller.addSongToPlaylist(playlist.id, s1.id)
        controller.addSongToPlaylist(playlist.id, s2.id)
        controller.selectPlaylist(playlist.id)
        assertEquals(100, controller.uiState.value.bpm)

        assertTrue(KeyboardShortcuts.handleKey(Key.PageDown, controller = controller))
        assertEquals(140, controller.uiState.value.bpm)

        assertTrue(KeyboardShortcuts.handleKey(Key.PageUp, controller = controller))
        assertEquals(100, controller.uiState.value.bpm)

        assertTrue(KeyboardShortcuts.handleKey(Key.RightBracket, controller = controller))
        assertEquals(140, controller.uiState.value.bpm)

        assertTrue(KeyboardShortcuts.handleKey(Key.LeftBracket, controller = controller))
        assertEquals(100, controller.uiState.value.bpm)
    }

    /** SPEC-UI-004 */
    @Test
    fun ctrlModifiedKeys_areIgnored() {
        assertFalse(KeyboardShortcuts.handleKey(Key.Spacebar, isCtrlPressed = true, controller = controller))
        assertFalse(controller.uiState.value.isPlaying)
    }
}

private class StubAudioEngine : IAudioEngine {
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

private class StubUpdateRepository : IUpdateRepository {
    override suspend fun checkForUpdate(currentVersionName: String, currentReleaseTag: String): AppReleaseInfo? = null
    override fun isVersionNewer(remoteTag: String, currentVersion: String, currentReleaseTag: String): Boolean = false
    override fun openUpdateUrl(downloadUrl: String) {}
}
