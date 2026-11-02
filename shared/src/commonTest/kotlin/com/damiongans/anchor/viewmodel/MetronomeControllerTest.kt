package com.damiongans.anchor.viewmodel

import com.damiongans.anchor.fakes.FakeAudioEngine
import com.damiongans.anchor.fakes.FakeHapticsManager
import com.damiongans.anchor.fakes.FakeSettingsRepository
import com.damiongans.anchor.fakes.FakeUpdateRepository
import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.ButtonTriggerMode
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.model.ThemeMode
import com.damiongans.anchor.model.TimeSignaturePreset
import com.damiongans.anchor.playback.NoOpPlaybackSession
import com.damiongans.anchor.update.AppReleaseInfo
import com.damiongans.anchor.update.AppVersionInfo
import com.damiongans.anchor.update.DistributionChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeControllerTest {

    private lateinit var scope: TestScope
    private lateinit var audio: FakeAudioEngine
    private lateinit var haptics: FakeHapticsManager
    private lateinit var settings: FakeSettingsRepository
    private lateinit var updates: FakeUpdateRepository
    private lateinit var session: NoOpPlaybackSession
    private lateinit var controller: MetronomeController

    @BeforeTest
    fun setUp() {
        scope = TestScope()
        audio = FakeAudioEngine()
        haptics = FakeHapticsManager()
        settings = FakeSettingsRepository()
        updates = FakeUpdateRepository()
        session = NoOpPlaybackSession()
        controller = newController()
    }

    private fun newController(
        engine: FakeAudioEngine = audio,
        autoCheck: Boolean = false,
        clock: () -> Long = { 0L },
        channel: DistributionChannel = DistributionChannel.GITHUB
    ) = MetronomeController(
        audioEngine = engine,
        hapticsManager = haptics,
        settingsRepository = settings,
        updateRepository = updates,
        appVersion = AppVersionInfo("1.4.0", channel = channel),
        scope = scope,
        playbackSession = session,
        clockMs = clock,
        autoCheckForUpdates = autoCheck
    )

    @Test
    fun playAndPause_controlsAudioEngineHapticsAndPlaybackSession() {
        assertFalse(controller.uiState.value.isPlaying)
        controller.togglePlayPause()
        assertTrue(controller.uiState.value.isPlaying)
        assertTrue(audio.isStarted)
        assertEquals(true, session.lastState?.isPlaying)

        controller.togglePlayPause()
        assertFalse(controller.uiState.value.isPlaying)
        assertFalse(audio.isStarted)
        assertTrue(haptics.isCancelled)
        assertEquals(false, session.lastState?.isPlaying)
    }

    @Test
    fun setBpm_clampsUpdatesMarkingAndPersists() {
        controller.setBpm(60)
        assertEquals(60, controller.uiState.value.bpm)
        assertEquals(60, settings.getBpm())
        assertEquals(60.0, audio.bpm, 0.01)
        assertEquals("Adagio", controller.uiState.value.tempoMarking)

        controller.adjustBpm(20)
        assertEquals("Andante", controller.uiState.value.tempoMarking)

        controller.multiplyBpm(2.0)
        assertEquals(160, controller.uiState.value.bpm)

        controller.setBpm(5)
        assertEquals(MetronomeController.MIN_BPM, controller.uiState.value.bpm)
        controller.setBpm(999)
        assertEquals(MetronomeController.MAX_BPM, controller.uiState.value.bpm)
        assertEquals("300 BPM • Prestissimo", session.lastState?.title)
    }

    @Test
    fun cycleBeatAccent_rotatesAndPersists() {
        controller.cycleBeatAccent(0)
        assertEquals(BeatAccent.REGULAR, controller.uiState.value.accents[0])
        assertEquals(BeatAccent.REGULAR, settings.getAccents(4)[0])
        controller.cycleBeatAccent(0)
        controller.cycleBeatAccent(0)
        assertEquals(BeatAccent.MUTE, controller.uiState.value.accents[0])
        controller.cycleBeatAccent(0)
        assertEquals(BeatAccent.ACCENT, controller.uiState.value.accents[0])
    }

    @Test
    fun timeSignatureChanges_preserveAccentsPerCount() {
        controller.cycleBeatAccent(1)
        controller.cycleBeatAccent(3)
        controller.cycleBeatAccent(3)

        controller.selectTimeSignature(TimeSignaturePreset.THREE_FOUR)
        assertEquals(listOf(BeatAccent.ACCENT, BeatAccent.SUB, BeatAccent.REGULAR), controller.uiState.value.accents)

        controller.selectTimeSignature(TimeSignaturePreset.SIX_EIGHT)
        val state = controller.uiState.value
        assertEquals(6, state.totalBeats)
        assertEquals(Subdivision.EIGHTH, state.currentSubdivision)
        assertEquals(8, state.timeSignatureDenominator)
        assertEquals(BeatAccent.SUB, state.accents[1])
        assertEquals(BeatAccent.MUTE, state.accents[3])
        assertEquals(6, audio.totalBeats)
        assertEquals("Paused • 6/8 Time", session.lastState?.subtitle)
    }

    @Test
    fun settings_persistAndRestoreAcrossControllerRecreation() {
        controller.setBpm(144)
        controller.toggleVibration()
        controller.toggleFlash()
        controller.setFlashDelay(-35)
        controller.setVibrationDelay(25)
        controller.setBluetoothCompensation(120)
        controller.setThemeMode(ThemeMode.LIGHT)
        controller.setAccentColor(AccentColorOption.GOLD)
        controller.setSoundSet(SoundSet.RIMSHOT)
        controller.setPitchMultiplier(5f)
        controller.setButtonTriggerMode(ButtonTriggerMode.ON_RELEASE)

        val restored = newController(engine = FakeAudioEngine())
        val state = restored.uiState.value
        assertEquals(144, state.bpm)
        assertTrue(state.isVibrationEnabled)
        assertTrue(state.isFlashEnabled)
        assertEquals(-35, state.flashDelayMs)
        assertEquals(25, state.vibrationDelayMs)
        assertEquals(120, state.bluetoothCompensationMs)
        assertEquals(ThemeMode.LIGHT, state.themeMode)
        assertEquals(AccentColorOption.GOLD, state.accentColor)
        assertEquals(SoundSet.RIMSHOT, state.currentSoundSet)
        assertEquals(2.0f, state.pitchMultiplier)
        assertEquals(ButtonTriggerMode.ON_RELEASE, state.buttonTriggerMode)

        restored.resetTimingOffsets()
        assertEquals(0, restored.uiState.value.flashDelayMs)
        assertEquals(0, settings.getVibrationDelayMs())
        assertEquals(0, settings.getBluetoothCompensationMs())
    }

    @Test
    fun beatTick_drivesVisualsFlashAndHaptics() {
        controller.toggleFlash()
        controller.toggleVibration()
        controller.play()

        audio.onBeatTick?.invoke(1, false, 0)
        assertEquals(1, controller.uiState.value.currentBeat)
        assertTrue(controller.uiState.value.flashPulse)
        assertEquals(listOf(false), haptics.ticks)

        scope.advanceTimeBy(MetronomeController.FLASH_DURATION_MS + 1)
        scope.runCurrent()
        assertFalse(controller.uiState.value.flashPulse)
    }

    @Test
    fun tapTempo_appliesBpmAndResetsStatus() {
        var now = 0L
        val tapController = newController(clock = { now })
        tapController.tapTempo()
        now = 500L
        tapController.tapTempo()
        assertEquals(120, tapController.uiState.value.bpm)
        assertEquals(2, tapController.uiState.value.tapStatus.tapCount)

        scope.advanceTimeBy(MetronomeController.TAP_RESET_MS + 1)
        scope.runCurrent()
        assertEquals(0, tapController.uiState.value.tapStatus.tapCount)
    }

    @Test
    fun playbackSessionCommands_controlTransportAndTempo() {
        val listener = assertNotNull(session.commandListener)
        listener.onAdjustBpm(1)
        assertEquals(121, controller.uiState.value.bpm)
        listener.onTogglePlayPause()
        assertTrue(controller.uiState.value.isPlaying)
        listener.onStop()
        assertFalse(controller.uiState.value.isPlaying)

        controller.release()
        assertNull(session.commandListener)
        assertTrue(audio.isReleased)
    }

    @Test
    fun updateFlow_showsDialogAndRemembersDecline() {
        updates.mockRelease = AppReleaseInfo(
            tagName = "v2.0.0",
            releaseName = "Anchor 2.0",
            releaseNotes = "Major update",
            releasePageUrl = "https://github.com/DamionGans/Anchor/releases",
            downloadUrl = "",
            isNewerVersion = true
        )
        val updating = newController(autoCheck = true)
        scope.advanceUntilIdle()

        assertEquals("v2.0.0", updating.uiState.value.availableUpdate?.tagName)
        assertTrue(updating.uiState.value.showUpdateDialog)

        updating.onAcceptUpdate()
        assertEquals("https://github.com/DamionGans/Anchor/releases", updates.openedUrl)

        updating.onDeclineUpdate()
        assertFalse(updating.uiState.value.showUpdateDialog)
        assertTrue(settings.isUpdateDeclined("v2.0.0"))
    }

    @Test
    fun manualUpdateCheck_reportsUpToDate() {
        controller.checkForUpdates(manual = true)
        scope.advanceUntilIdle()
        assertEquals("Anchor is up to date (v1.4.0)", controller.uiState.value.updateCheckMessage)
        assertFalse(controller.uiState.value.isCheckingForUpdates)
    }

    @Test
    fun storeBuilds_neverCheckGitHubForUpdates() {
        updates.mockRelease = AppReleaseInfo(
            tagName = "v2.0.0",
            releaseName = "Anchor 2.0",
            releaseNotes = "",
            releasePageUrl = "https://github.com/DamionGans/Anchor/releases",
            downloadUrl = "https://github.com/DamionGans/Anchor/releases/download/v2.0.0/app.apk",
            isNewerVersion = true
        )
        for (channel in listOf(DistributionChannel.PLAY_STORE, DistributionChannel.APP_STORE, DistributionChannel.FDROID)) {
            val store = newController(autoCheck = true, channel = channel)
            store.checkForUpdates(manual = true)
            scope.advanceUntilIdle()
            val state = store.uiState.value
            assertFalse(state.canCheckForUpdates)
            assertEquals(channel.storeName, state.updateStoreName)
            assertNull(state.availableUpdate)
            assertFalse(state.showUpdateDialog)
            assertNull(state.updateCheckMessage)
        }
        assertTrue(controller.uiState.value.canCheckForUpdates)
        assertEquals(DistributionChannel.PLAY_STORE, DistributionChannel.fromName("play_store"))
        assertEquals(DistributionChannel.GITHUB, DistributionChannel.fromName(""))
    }
}
