package com.damiongans.anchor.fakes

import com.damiongans.anchor.audio.IAudioEngine
import com.damiongans.anchor.haptics.IHapticsManager
import com.damiongans.anchor.model.AccentColorOption
import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputDevice
import com.damiongans.anchor.model.AudioOutputInfo
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.ButtonTriggerMode
import com.damiongans.anchor.model.SoundSet
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.model.ThemeMode
import com.damiongans.anchor.repository.ISettingsRepository
import com.damiongans.anchor.update.AppReleaseInfo
import com.damiongans.anchor.update.IUpdateRepository

class FakeAudioEngine : IAudioEngine {
    override var bpm: Double = 120.0
    override var totalBeats: Int = 4
    override var currentSubdivision: Subdivision = Subdivision.QUARTER
    override var accents: List<BeatAccent> = listOf(BeatAccent.ACCENT, BeatAccent.REGULAR, BeatAccent.REGULAR, BeatAccent.REGULAR)
    override var currentSoundSet: SoundSet = SoundSet.WOODBLOCK
    override var pitchMultiplier: Float = 1.0f
    override var visualDelayMs: Int = 0
    override var vibrationDelayMs: Int = 0
    override var manualBluetoothCompensationMs: Int = 0
    var selectedOutputDeviceId: Int? = -1
    override var currentAudioInfo: AudioOutputInfo = AudioOutputInfo(
        deviceType = AudioDeviceType.SPEAKER,
        selectedDeviceId = -1,
        availableDevices = listOf(
            AudioOutputDevice(id = -1, deviceType = AudioDeviceType.SPEAKER, name = "Built-in Speaker")
        )
    )

    private var _onBeatTick: ((beatIndex: Int, isAccent: Boolean, subdivisionIndex: Int) -> Unit)? = null
    override var onBeatTick: ((beatIndex: Int, isAccent: Boolean, subdivisionIndex: Int) -> Unit)?
        get() = { beatIndex, isAccent, subIdx ->
            _onBeatTick?.invoke(beatIndex, isAccent, subIdx)
            if (subIdx == 0) {
                onVibrationTick?.invoke(beatIndex, isAccent)
            }
        }
        set(value) {
            _onBeatTick = value
        }

    override var onVibrationTick: ((beatIndex: Int, isAccent: Boolean) -> Unit)? = null
    override var onPhaseUpdate: ((beatIndex: Int, phase: Float) -> Unit)? = null
    override var onBarCompleted: (() -> Unit)? = null
    override var onAudioDeviceChanged: ((AudioOutputInfo) -> Unit)? = null

    var isStarted: Boolean = false
    var isReleased: Boolean = false

    fun invokeVisualTickOnly(beatIndex: Int, isAccent: Boolean, subdivisionIndex: Int) {
        _onBeatTick?.invoke(beatIndex, isAccent, subdivisionIndex)
    }

    override fun start() {
        isStarted = true
    }

    override fun stop() {
        isStarted = false
    }

    override fun release() {
        isReleased = true
        isStarted = false
    }

    override fun detectCurrentAudioDevice() {
        val comp = if (currentAudioInfo.deviceType == AudioDeviceType.BLUETOOTH) {
            manualBluetoothCompensationMs
        } else {
            0
        }
        val guaranteedDevices = currentAudioInfo.availableDevices.ifEmpty {
            listOf(AudioOutputDevice(id = -1, deviceType = AudioDeviceType.SPEAKER, name = "Built-in Speaker"))
        }
        currentAudioInfo = currentAudioInfo.copy(
            compensationMs = comp,
            availableDevices = guaranteedDevices
        )
        onAudioDeviceChanged?.invoke(currentAudioInfo)
    }

    override fun selectAudioOutputDevice(deviceId: Int?) {
        selectedOutputDeviceId = deviceId
        val matched = currentAudioInfo.availableDevices.firstOrNull { it.id == deviceId }
        if (matched != null) {
            currentAudioInfo = currentAudioInfo.copy(
                deviceType = matched.deviceType,
                deviceName = matched.name,
                selectedDeviceId = matched.id
            )
        }
        detectCurrentAudioDevice()
    }
}

class FakeHapticsManager : IHapticsManager {
    val ticks = mutableListOf<Boolean>() // true for accent, false for normal
    var isCancelled: Boolean = false

    override fun triggerTick(isAccent: Boolean) {
        ticks.add(isAccent)
    }

    override fun cancel() {
        isCancelled = true
    }
}

class FakeSettingsRepository(
    private var currentBpm: Int = 120,
    private var currentTotalBeats: Int = 4,
    private var currentSubdivision: Subdivision = Subdivision.QUARTER,
    private var currentAccents: List<BeatAccent>? = null,
    private var currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    private var currentAccentColor: AccentColorOption = AccentColorOption.SYSTEM,
    private var currentFlashDelayMs: Int = 0,
    private var currentVibrationDelayMs: Int = 0,
    private var currentBluetoothCompMs: Int = 0,
    private var currentVibrationEnabled: Boolean = false,
    private var currentFlashEnabled: Boolean = false,
    private var currentSoundSet: SoundSet = SoundSet.WOODBLOCK,
    private var currentPitchMultiplier: Float = 1.0f,
    private var currentButtonTriggerMode: ButtonTriggerMode = ButtonTriggerMode.ON_PRESS
) : ISettingsRepository {

    private val rememberedAccentsList: MutableList<BeatAccent> = run {
        val defaults = ISettingsRepository.defaultAccentsForBeats(
            ISettingsRepository.MAX_BEATS
        ).toMutableList()
        val initial = currentAccents
        if (initial != null) {
            for (i in 0 until minOf(initial.size, defaults.size)) {
                defaults[i] = initial[i]
            }
        }
        defaults
    }

    val declinedTags = mutableSetOf<String>()

    override fun getBpm(): Int = currentBpm
    override fun setBpm(bpm: Int) { currentBpm = bpm }

    override fun getTotalBeats(): Int = currentTotalBeats
    override fun setTotalBeats(beats: Int) { currentTotalBeats = beats }

    override fun getSubdivision(): Subdivision = currentSubdivision
    override fun setSubdivision(subdivision: Subdivision) { currentSubdivision = subdivision }

    override fun getRememberedAccents(): List<BeatAccent> = rememberedAccentsList.toList()
    override fun setRememberedAccents(accents: List<BeatAccent>) {
        for (i in 0 until minOf(accents.size, rememberedAccentsList.size)) {
            rememberedAccentsList[i] = accents[i]
        }
    }

    override fun getAccents(totalBeats: Int): List<BeatAccent> {
        val count = totalBeats.coerceIn(1, ISettingsRepository.MAX_BEATS)
        return rememberedAccentsList.take(count)
    }
    override fun setAccents(accents: List<BeatAccent>) {
        currentAccents = accents.toList()
        for (i in 0 until minOf(accents.size, rememberedAccentsList.size)) {
            rememberedAccentsList[i] = accents[i]
        }
    }

    override fun getThemeMode(): ThemeMode = currentThemeMode
    override fun setThemeMode(mode: ThemeMode) { currentThemeMode = mode }

    override fun getAccentColor(): AccentColorOption = currentAccentColor
    override fun setAccentColor(accent: AccentColorOption) { currentAccentColor = accent }

    override fun getFlashDelayMs(): Int = currentFlashDelayMs
    override fun setFlashDelayMs(delayMs: Int) { currentFlashDelayMs = delayMs }

    override fun getVibrationDelayMs(): Int = currentVibrationDelayMs
    override fun setVibrationDelayMs(delayMs: Int) { currentVibrationDelayMs = delayMs }

    override fun getBluetoothCompensationMs(): Int = currentBluetoothCompMs
    override fun setBluetoothCompensationMs(offsetMs: Int) { currentBluetoothCompMs = offsetMs }

    override fun isVibrationEnabled(): Boolean = currentVibrationEnabled
    override fun setVibrationEnabled(enabled: Boolean) { currentVibrationEnabled = enabled }

    override fun isFlashEnabled(): Boolean = currentFlashEnabled
    override fun setFlashEnabled(enabled: Boolean) { currentFlashEnabled = enabled }

    override fun getSoundSet(): SoundSet = currentSoundSet
    override fun setSoundSet(soundSet: SoundSet) { currentSoundSet = soundSet }

    override fun getPitchMultiplier(): Float = currentPitchMultiplier
    override fun setPitchMultiplier(pitch: Float) { currentPitchMultiplier = pitch }

    override fun getButtonTriggerMode(): ButtonTriggerMode = currentButtonTriggerMode
    override fun setButtonTriggerMode(mode: ButtonTriggerMode) { currentButtonTriggerMode = mode }

    override fun isUpdateDeclined(versionTag: String): Boolean = declinedTags.contains(versionTag)
    override fun setUpdateDeclined(versionTag: String, declined: Boolean) {
        if (declined) declinedTags.add(versionTag) else declinedTags.remove(versionTag)
    }
}

class FakeUpdateRepository(
    var mockRelease: AppReleaseInfo? = null
) : IUpdateRepository {

    var openedUrl: String? = null

    override suspend fun checkForUpdate(currentVersionName: String, currentReleaseTag: String): AppReleaseInfo? {
        return mockRelease
    }

    override fun isVersionNewer(remoteTag: String, currentVersion: String, currentReleaseTag: String): Boolean {
        return mockRelease?.isNewerVersion ?: false
    }

    override fun openUpdateUrl(downloadUrl: String) {
        openedUrl = downloadUrl
    }
}
