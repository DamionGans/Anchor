package com.damiongans.anchor.viewmodel

import com.damiongans.anchor.fakes.FakeAudioEngine
import com.damiongans.anchor.fakes.FakeHapticsManager
import com.damiongans.anchor.fakes.FakeSettingsRepository
import com.damiongans.anchor.fakes.FakeUpdateRepository
import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputDevice
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

    /** SPEC-SESSION-001, SPEC-TRANSPORT-001 */
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

    /** SPEC-TRANSPORT-001 */
    @Test
    fun setOutputStandby_isPassedToTheAudioEngine() {
        controller.setOutputStandby(true)
        assertTrue(audio.standbyEnabled)
        controller.play()
        assertTrue(audio.isStarted)
        controller.setOutputStandby(false)
        assertFalse(audio.standbyEnabled)
        assertTrue(audio.isStarted, "Leaving the foreground must not stop playback")
    }

    /** SPEC-TEMPO-001, SPEC-TEMPO-002 */
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
        scope.advanceUntilIdle()
        assertEquals("300 BPM • Prestissimo", session.lastState?.title)
    }

    /** SPEC-ACCENT-001 */
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

    /** SPEC-ACCENT-002 */
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
        scope.advanceUntilIdle()
        assertEquals("Paused • 6/8 Time", session.lastState?.subtitle)
    }

    /** SPEC-PERSIST-001, SPEC-AUDIO-005, SPEC-FEEDBACK-001 */
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

    /** SPEC-TIMING-001, SPEC-FEEDBACK-001 */
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

    /** SPEC-TEMPO-004 */
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

    /** SPEC-SESSION-001 */
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

    /** SPEC-UPDATE-001, SPEC-UPDATE-002 */
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

    /** SPEC-UPDATE-002 */
    @Test
    fun manualUpdateCheck_reportsUpToDate() {
        controller.checkForUpdates(manual = true)
        scope.advanceUntilIdle()
        assertEquals("Anchor is up to date (v1.4.0)", controller.uiState.value.updateCheckMessage)
        assertFalse(controller.uiState.value.isCheckingForUpdates)
    }

    /** SPEC-UPDATE-002, SPEC-PROD-002 */
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

    /** SPEC-PULSE-003 */
    @Test
    fun meterChangesWhilePlaying_doNotResetTheVisualBeat() {
        controller.play()
        audio.onBeatTick?.invoke(2, false, 1)
        controller.selectTimeSignature(TimeSignaturePreset.SIX_EIGHT)
        controller.setSubdivision(Subdivision.SIXTEENTH)
        val state = controller.uiState.value
        assertEquals(2, state.currentBeat)
        assertEquals(1, state.currentSubdivisionIndex)
        assertTrue(state.isPlaying)
        assertTrue(audio.isStarted)
    }

    /** SPEC-SESSION-001 */
    @Test
    fun rapidBpmChanges_updateTheMediaSessionOnceAfterSettling() {
        val updatesBefore = session.updateCount
        for (bpm in 100..140) controller.setBpm(bpm)
        assertEquals(140, controller.uiState.value.bpm)
        assertEquals(140.0, audio.bpm, 0.01)
        assertEquals(updatesBefore, session.updateCount, "no media-session rebuild while still changing")

        scope.advanceTimeBy(MetronomeController.SESSION_SYNC_DEBOUNCE_MS + 1)
        scope.runCurrent()
        assertEquals(updatesBefore + 1, session.updateCount)
        assertEquals("140 BPM • Allegro", session.lastState?.title)

        // Play / pause still update immediately.
        controller.play()
        assertEquals(updatesBefore + 2, session.updateCount)
        assertEquals(true, session.lastState?.isPlaying)
    }

    /** SPEC-TEMPO-002 */
    @Test
    fun adjustAndMultiplyBpm_deriveFromCurrentTempoAndClamp() {
        controller.setBpm(100)
        controller.adjustBpm(5)
        assertEquals(105, controller.uiState.value.bpm)
        controller.multiplyBpm(2.0)
        assertEquals(210, controller.uiState.value.bpm)
        controller.multiplyBpm(2.0)
        assertEquals(MetronomeController.MAX_BPM, controller.uiState.value.bpm)
        controller.adjustBpm(-1000)
        assertEquals(MetronomeController.MIN_BPM, controller.uiState.value.bpm)
        assertEquals(MetronomeController.MIN_BPM, settings.getBpm())
    }

    /** SPEC-TEMPO-005 */
    @Test
    fun buttonTriggerMode_defaultsToOnPressAndPersists() {
        assertEquals(ButtonTriggerMode.ON_PRESS, controller.uiState.value.buttonTriggerMode)
        controller.setButtonTriggerMode(ButtonTriggerMode.ON_RELEASE)
        assertEquals(ButtonTriggerMode.ON_RELEASE, controller.uiState.value.buttonTriggerMode)
        assertEquals(ButtonTriggerMode.ON_RELEASE, newController().uiState.value.buttonTriggerMode)
    }

    /** SPEC-METER-001 */
    @Test
    fun beatCount_isClampedTo1Through16() {
        assertEquals(4, controller.uiState.value.totalBeats)
        controller.setTimeSignature(0, Subdivision.QUARTER)
        assertEquals(1, controller.uiState.value.totalBeats)
        controller.setTimeSignature(40, Subdivision.QUARTER)
        assertEquals(16, controller.uiState.value.totalBeats)
        assertEquals(16, audio.totalBeats)
        assertEquals(16, controller.uiState.value.accents.size)
    }

    /** SPEC-TIMING-002 */
    @Test
    fun timingOffsets_areClampedToTheirRangesAndResetToZero() {
        controller.setFlashDelay(-999)
        controller.setVibrationDelay(999)
        controller.setBluetoothCompensation(999)
        with(controller.uiState.value) {
            assertEquals(-200, flashDelayMs)
            assertEquals(300, vibrationDelayMs)
            assertEquals(400, bluetoothCompensationMs)
        }
        controller.setBluetoothCompensation(-999)
        assertEquals(-100, controller.uiState.value.bluetoothCompensationMs)

        controller.resetTimingOffsets()
        with(controller.uiState.value) {
            assertEquals(0, flashDelayMs)
            assertEquals(0, vibrationDelayMs)
            assertEquals(0, bluetoothCompensationMs)
        }
    }

    /** SPEC-AUDIO-006, SPEC-TIMING-003 */
    @Test
    fun selectingAnOutput_routesTheEngineAndUpdatesTheReportedRoute() {
        val headset = AudioOutputDevice(id = 7, deviceType = AudioDeviceType.BLUETOOTH, name = "Headset")
        audio.currentAudioInfo = audio.currentAudioInfo.copy(
            availableDevices = audio.currentAudioInfo.availableDevices + headset
        )
        controller.setBluetoothCompensation(120)

        controller.selectAudioOutputDevice(7)
        assertEquals(7, audio.selectedOutputDeviceId)
        assertEquals(AudioDeviceType.BLUETOOTH, controller.uiState.value.audioInfo.deviceType)
        assertEquals(120, controller.uiState.value.effectiveVisualDelayMs)

        controller.selectAudioOutputDevice(-1)
        assertEquals(AudioDeviceType.SPEAKER, controller.uiState.value.audioInfo.deviceType)
        assertEquals(0, controller.uiState.value.effectiveVisualDelayMs)
    }
}
