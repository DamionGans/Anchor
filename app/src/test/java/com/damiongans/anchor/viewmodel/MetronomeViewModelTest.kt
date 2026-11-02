package com.damiongans.anchor.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
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
import com.damiongans.anchor.service.MetronomePlaybackService
import com.damiongans.anchor.update.AppReleaseInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetronomeViewModelTest {

    private lateinit var app: Application
    private lateinit var fakeAudioEngine: FakeAudioEngine
    private lateinit var fakeHaptics: FakeHapticsManager
    private lateinit var fakeSettings: FakeSettingsRepository
    private lateinit var fakeUpdates: FakeUpdateRepository
    private lateinit var viewModel: MetronomeViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        fakeAudioEngine = FakeAudioEngine()
        fakeHaptics = FakeHapticsManager()
        fakeSettings = FakeSettingsRepository()
        fakeUpdates = FakeUpdateRepository()

        viewModel = MetronomeViewModel(
            application = app,
            audioEngine = fakeAudioEngine,
            hapticsManager = fakeHaptics,
            settingsRepository = fakeSettings,
            updateRepository = fakeUpdates
        )
    }

    @Test
    fun playAndPause_controlsAudioEngineAndState() {
        assertFalse(viewModel.uiState.value.isPlaying)
        assertFalse(fakeAudioEngine.isStarted)

        viewModel.togglePlayPause()
        assertTrue(viewModel.uiState.value.isPlaying)
        assertTrue(fakeAudioEngine.isStarted)

        viewModel.togglePlayPause()
        assertFalse(viewModel.uiState.value.isPlaying)
        assertFalse(fakeAudioEngine.isStarted)
        assertTrue(fakeHaptics.isCancelled)
    }

    @Test
    fun setBpm_clampsValueAndUpdatesTempoMarkingAndPersists() {
        viewModel.setBpm(60)
        assertEquals(60, viewModel.uiState.value.bpm)
        assertEquals(60, fakeSettings.getBpm())
        assertEquals(60.0, fakeAudioEngine.bpm, 0.01)
        assertEquals("Adagio", viewModel.uiState.value.tempoMarking)

        viewModel.adjustBpm(20)
        assertEquals(80, viewModel.uiState.value.bpm)
        assertEquals("Andante", viewModel.uiState.value.tempoMarking)

        viewModel.multiplyBpm(2.0)
        assertEquals(160, viewModel.uiState.value.bpm)
        assertEquals(160, fakeSettings.getBpm())
    }

    @Test
    fun cycleBeatAccent_rotatesThroughStatesAndPersists() {
        assertEquals(BeatAccent.ACCENT, viewModel.uiState.value.accents[0])

        viewModel.cycleBeatAccent(0)
        assertEquals(BeatAccent.REGULAR, viewModel.uiState.value.accents[0])
        assertEquals(BeatAccent.REGULAR, fakeSettings.getAccents(4)[0])

        viewModel.cycleBeatAccent(0)
        assertEquals(BeatAccent.SUB, viewModel.uiState.value.accents[0])

        viewModel.cycleBeatAccent(0)
        assertEquals(BeatAccent.MUTE, viewModel.uiState.value.accents[0])

        viewModel.cycleBeatAccent(0)
        assertEquals(BeatAccent.ACCENT, viewModel.uiState.value.accents[0])
    }

    @Test
    fun selectTimeSignature_preservesAccentsByCountAcrossChanges() {
        // Initial 4/4: [ACCENT, REGULAR, REGULAR, REGULAR]
        assertEquals(
            listOf(BeatAccent.ACCENT, BeatAccent.REGULAR, BeatAccent.REGULAR, BeatAccent.REGULAR),
            viewModel.uiState.value.accents
        )

        // Customize Beat 2 (idx 1 -> SUB) and Beat 4 (idx 3 -> MUTE)
        viewModel.cycleBeatAccent(1) // REGULAR -> SUB
        viewModel.cycleBeatAccent(3) // REGULAR -> SUB
        viewModel.cycleBeatAccent(3) // SUB -> MUTE
        assertEquals(BeatAccent.SUB, viewModel.uiState.value.accents[1])
        assertEquals(BeatAccent.MUTE, viewModel.uiState.value.accents[3])

        // Switch to 3/4 (3 beats): Beat 2 remains SUB, Beat 4 is remembered in background
        viewModel.selectTimeSignature(TimeSignaturePreset.THREE_FOUR)
        assertEquals(3, viewModel.uiState.value.totalBeats)
        assertEquals(
            listOf(BeatAccent.ACCENT, BeatAccent.SUB, BeatAccent.REGULAR),
            viewModel.uiState.value.accents
        )

        // Switch to 6/8 (6 beats): Beat 2 (SUB) and Beat 4 (MUTE) are restored!
        viewModel.selectTimeSignature(TimeSignaturePreset.SIX_EIGHT)
        assertEquals(6, viewModel.uiState.value.totalBeats)
        assertEquals(Subdivision.EIGHTH, viewModel.uiState.value.currentSubdivision)
        assertEquals(6, fakeAudioEngine.totalBeats)
        assertEquals(6, fakeSettings.getTotalBeats())
        assertEquals(Subdivision.EIGHTH, fakeSettings.getSubdivision())
        assertEquals(BeatAccent.ACCENT, viewModel.uiState.value.accents[0])
        assertEquals(BeatAccent.SUB, viewModel.uiState.value.accents[1])
        assertEquals(BeatAccent.REGULAR, viewModel.uiState.value.accents[2])
        assertEquals(BeatAccent.MUTE, viewModel.uiState.value.accents[3])
        assertEquals(BeatAccent.REGULAR, viewModel.uiState.value.accents[4])
        assertEquals(BeatAccent.REGULAR, viewModel.uiState.value.accents[5])
    }

    @Test
    fun buttonTriggerMode_andOutputSelection_updateAndPersist() {
        assertEquals(
            ButtonTriggerMode.ON_PRESS,
            viewModel.uiState.value.buttonTriggerMode
        )
        // Always has at least 1 available output (e.g. Built-in Speaker)
        assertTrue(viewModel.uiState.value.audioInfo.availableDevices.isNotEmpty())

        viewModel.setButtonTriggerMode(ButtonTriggerMode.ON_RELEASE)
        assertEquals(
            ButtonTriggerMode.ON_RELEASE,
            viewModel.uiState.value.buttonTriggerMode
        )
        assertEquals(
            ButtonTriggerMode.ON_RELEASE,
            fakeSettings.getButtonTriggerMode()
        )

        viewModel.selectAudioOutputDevice(-1)
        assertEquals(-1, fakeAudioEngine.selectedOutputDeviceId)
    }

    @Test
    fun themeAndSoundSettings_persistViaRepository() {
        viewModel.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
        assertEquals(ThemeMode.LIGHT, fakeSettings.getThemeMode())

        viewModel.setAccentColor(AccentColorOption.GOLD)
        assertEquals(AccentColorOption.GOLD, viewModel.uiState.value.accentColor)
        assertEquals(AccentColorOption.GOLD, fakeSettings.getAccentColor())

        viewModel.setSoundSet(SoundSet.RIMSHOT)
        assertEquals(SoundSet.RIMSHOT, viewModel.uiState.value.currentSoundSet)
        assertEquals(SoundSet.RIMSHOT, fakeSettings.getSoundSet())
    }

    @Test
    fun separateFlashAndVibrationDelays_andSettingsPersistAcrossViewModelRecreate() {
        viewModel.setBpm(144)
        viewModel.toggleVibration()
        viewModel.toggleFlash()
        viewModel.setFlashDelay(-35)
        viewModel.setVibrationDelay(25)
        viewModel.setBluetoothCompensation(120)

        // Recreate a new ViewModel backed by the same settings repository (simulating app restart)
        val restoredVm = MetronomeViewModel(
            application = app,
            audioEngine = FakeAudioEngine(),
            hapticsManager = FakeHapticsManager(),
            settingsRepository = fakeSettings,
            updateRepository = fakeUpdates
        )

        val state = restoredVm.uiState.value
        assertEquals(144, state.bpm)
        assertTrue(state.isVibrationEnabled)
        assertTrue(state.isFlashEnabled)
        assertEquals(-35, state.flashDelayMs)
        assertEquals(25, state.vibrationDelayMs)
        assertEquals(120, state.bluetoothCompensationMs)

        // Verify resetTimingOffsets restores all three to 0ms
        restoredVm.resetTimingOffsets()
        assertEquals(0, restoredVm.uiState.value.flashDelayMs)
        assertEquals(0, restoredVm.uiState.value.vibrationDelayMs)
        assertEquals(0, restoredVm.uiState.value.bluetoothCompensationMs)
        assertEquals(0, fakeSettings.getFlashDelayMs())
        assertEquals(0, fakeSettings.getVibrationDelayMs())
        assertEquals(0, fakeSettings.getBluetoothCompensationMs())
    }

    @Test
    fun beatTick_synchronizesFlashPulseWithMainScreenBeatVisuals() {
        viewModel.toggleFlash()
        viewModel.toggleVibration()
        viewModel.setFlashDelay(0)
        viewModel.setVibrationDelay(0)
        viewModel.play()

        // Trigger Beat 2 (index 1) from audio engine
        fakeAudioEngine.onBeatTick?.invoke(1, false, 0)

        assertEquals(1, viewModel.uiState.value.currentBeat)
        assertFalse(viewModel.uiState.value.isCurrentBeatAccent)
        assertTrue(viewModel.uiState.value.flashPulse)
        assertEquals(1, fakeHaptics.ticks.size)
        assertFalse(fakeHaptics.ticks[0])
    }

    @Test
    fun updateFlow_decliningSavesTagToSettings() {
        fakeUpdates.mockRelease = AppReleaseInfo(
            tagName = "v2.0.0",
            releaseName = "Anchor 2.0",
            releaseNotes = "Major update",
            releasePageUrl = "https://github.com/DamionGans/Anchor/releases",
            downloadUrl = "https://github.com/DamionGans/Anchor/releases/download/v2.0.0/app.apk",
            isNewerVersion = true
        )

        viewModel.checkForUpdates(manual = false)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("v2.0.0", viewModel.uiState.value.availableUpdate?.tagName)
        assertTrue(viewModel.uiState.value.showUpdateDialog)

        viewModel.onDeclineUpdate()
        assertFalse(viewModel.uiState.value.showUpdateDialog)
        assertTrue(viewModel.uiState.value.isUpdateDeclined)
        assertTrue(fakeSettings.isUpdateDeclined("v2.0.0"))
    }

    @Test
    fun notificationMediaControls_playPauseAndAdjustBpmAndDisplayTempo() {
        val service = org.robolectric.Robolectric.buildService(
            MetronomePlaybackService::class.java
        ).create().get()

        // 1. Verify notification displays current tempo and 3 multimedia actions (-1 BPM, Play/Pause, +1 BPM)
        val playingNotif = service.buildNotification(bpm = 128, beats = 4, denominator = 4, isPlaying = true)
        assertEquals("128 BPM • Allegro", playingNotif.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals("Playing • 4/4 Time", playingNotif.extras.getString(android.app.Notification.EXTRA_TEXT))
        assertEquals(3, playingNotif.actions.size)
        assertEquals("-1 BPM", playingNotif.actions[0].title.toString())
        assertEquals("Pause", playingNotif.actions[1].title.toString())
        assertEquals("+1 BPM", playingNotif.actions[2].title.toString())

        val pausedNotif = service.buildNotification(bpm = 96, beats = 6, denominator = 8, isPlaying = false)
        assertEquals("96 BPM • Moderato", pausedNotif.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertEquals("Paused • 6/8 Time", pausedNotif.extras.getString(android.app.Notification.EXTRA_TEXT))
        assertEquals(3, pausedNotif.actions.size)
        assertEquals("-1 BPM", pausedNotif.actions[0].title.toString())
        assertEquals("Play", pausedNotif.actions[1].title.toString())
        assertEquals("+1 BPM", pausedNotif.actions[2].title.toString())

        // 2. Verify notification service actions control ViewModel Play/Pause and Tempo +/-
        assertEquals(120, viewModel.uiState.value.bpm)
        assertFalse(viewModel.uiState.value.isPlaying)

        // Increase tempo from notification drawer
        service.onStartCommand(
            android.content.Intent(app, MetronomePlaybackService::class.java).apply {
                action = MetronomePlaybackService.ACTION_INCREASE_TEMPO
            },
            0,
            1
        )
        assertEquals(121, viewModel.uiState.value.bpm)

        // Decrease tempo from notification drawer
        service.onStartCommand(
            android.content.Intent(app, MetronomePlaybackService::class.java).apply {
                action = MetronomePlaybackService.ACTION_DECREASE_TEMPO
            },
            0,
            2
        )
        assertEquals(120, viewModel.uiState.value.bpm)

        // Toggle Play from notification drawer
        service.onStartCommand(
            android.content.Intent(app, MetronomePlaybackService::class.java).apply {
                action = MetronomePlaybackService.ACTION_TOGGLE_PLAY_PAUSE
            },
            0,
            3
        )
        assertTrue(viewModel.uiState.value.isPlaying)
        assertTrue(fakeAudioEngine.isStarted)

        // Toggle Pause from notification drawer
        service.onStartCommand(
            android.content.Intent(app, MetronomePlaybackService::class.java).apply {
                action = MetronomePlaybackService.ACTION_TOGGLE_PLAY_PAUSE
            },
            0,
            4
        )
        assertFalse(viewModel.uiState.value.isPlaying)
        assertFalse(fakeAudioEngine.isStarted)
    }
}
