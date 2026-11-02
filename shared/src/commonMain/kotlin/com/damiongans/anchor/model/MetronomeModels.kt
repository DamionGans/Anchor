package com.damiongans.anchor.model

/**
 * Sound set options for the metronome.
 * Pure Kotlin model (platform-agnostic for Android, iOS, and Desktop).
 */
enum class SoundSet(val displayName: String, val description: String) {
    WOODBLOCK("Woodblock", "Classic acoustic wood clave"),
    MECHANICAL("Mechanical", "Traditional pendulum click"),
    DIGITAL("Digital Beep", "Clear synthetic pulse"),
    RIMSHOT("Rimshot", "Snare rim strike"),
    ELECTRO_808("808 Percussion", "Vintage analog drum clave")
}

/**
 * State of an individual beat within a measure.
 */
enum class BeatAccent(val label: String) {
    ACCENT("Accent"),
    REGULAR("Normal"),
    SUB("Soft"),
    MUTE("Mute");

    fun next(): BeatAccent {
        val vals = entries
        return vals[(ordinal + 1) % vals.size]
    }
}

/**
 * Common subdivision options per beat.
 */
enum class Subdivision(val count: Int, val displayName: String, val symbol: String) {
    QUARTER(1, "Quarter", "♩"),
    EIGHTH(2, "Eighth", "♫"),
    TRIPLET(3, "Triplet", "3"),
    SIXTEENTH(4, "16th", "♬")
}

/**
 * Common musical time signature presets.
 */
enum class TimeSignaturePreset(val beats: Int, val subdivision: Subdivision, val displayName: String) {
    ONE_FOUR(1, Subdivision.QUARTER, "1/4"),
    TWO_FOUR(2, Subdivision.QUARTER, "2/4"),
    THREE_FOUR(3, Subdivision.QUARTER, "3/4"),
    FOUR_FOUR(4, Subdivision.QUARTER, "4/4"),
    FIVE_FOUR(5, Subdivision.QUARTER, "5/4"),
    SIX_EIGHT(6, Subdivision.EIGHTH, "6/8"),
    SEVEN_EIGHT(7, Subdivision.EIGHTH, "7/8"),
    NINE_EIGHT(9, Subdivision.EIGHTH, "9/8"),
    TWELVE_EIGHT(12, Subdivision.EIGHTH, "12/8")
}

/**
 * Italian tempo markings corresponding to standard BPM ranges.
 */
data class TempoMarking(val name: String, val minBpm: Int, val maxBpm: Int) {
    companion object {
        val ALL = listOf(
            TempoMarking("Larghissimo", 20, 39),
            TempoMarking("Grave", 40, 49),
            TempoMarking("Largo", 50, 59),
            TempoMarking("Adagio", 60, 71),
            TempoMarking("Andante", 72, 87),
            TempoMarking("Moderato", 88, 107),
            TempoMarking("Allegretto", 108, 119),
            TempoMarking("Allegro", 120, 155),
            TempoMarking("Vivace", 156, 175),
            TempoMarking("Presto", 176, 199),
            TempoMarking("Prestissimo", 200, 300)
        )

        fun forBpm(bpm: Int): String {
            return ALL.find { bpm in it.minBpm..it.maxBpm }?.name ?: if (bpm < 20) "Larghissimo" else "Prestissimo"
        }
    }
}

/**
 * Output audio device categories and estimated hardware path latency.
 */
enum class AudioDeviceType(val displayName: String, val latencyCategory: String, val defaultCompensationMs: Int) {
    SPEAKER("Built-in Speaker", "Low Latency (~4ms)", 0),
    WIRED_HEADPHONES("Wired Headphones", "Low Latency (~3ms)", 0),
    USB_AUDIO("USB Audio DAC", "Studio Latency (~2.5ms)", 0),
    BLUETOOTH("Bluetooth Audio", "Wireless A2DP (~85ms)", 85),
    OTHER("Audio Output", "Direct Output", 0)
}

/**
 * Represents a selectable hardware audio output route.
 */
data class AudioOutputDevice(
    val id: Int,
    val deviceType: AudioDeviceType,
    val name: String
)

data class AudioOutputInfo(
    val deviceType: AudioDeviceType = AudioDeviceType.SPEAKER,
    val deviceName: String = "Built-in Speaker",
    val sampleRate: Int = 48000,
    val bufferFrames: Int = 256,
    val isProAudioSupported: Boolean = false,
    val compensationMs: Int = 0,
    val selectedDeviceId: Int? = -1,
    val availableDevices: List<AudioOutputDevice> = listOf(
        AudioOutputDevice(id = -1, deviceType = AudioDeviceType.SPEAKER, name = "Built-in Speaker")
    )
)

/**
 * Controls whether the Play/Pause and Tap Tempo buttons register on finger touch-down (tab)
 * or on finger release.
 */
enum class ButtonTriggerMode(val displayName: String, val shortLabel: String, val description: String) {
    ON_PRESS("On Button Tap (Press)", "On Tap (Press)", "Registers immediately when your finger touches the button"),
    ON_RELEASE("On Button Release", "On Release", "Registers when your finger lifts off the button")
}

/**
 * Tap tempo status and pulse detection certainty.
 */
data class TapTempoStatus(
    val tapCount: Int = 0,
    val lastTapTime: Long = 0L,
    val estimatedBpm: Int = 120,
    val confidencePercent: Int = 0,
    val isHighCertainty: Boolean = false,
    val pulseDescription: String = ""
)

/**
 * Theme mode selection.
 */
enum class ThemeMode(val displayName: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

/**
 * Accent color options for UI personalization.
 * Uses platform-agnostic 32-bit ARGB hex values so core models have zero UI toolkit dependencies.
 */
enum class AccentColorOption(val displayName: String, val argbHex: Long, val isDynamic: Boolean = false) {
    SYSTEM("Phone Theme", 0x00000000L, isDynamic = true),
    CYAN("Neon Cyan", 0xFF00E5FFL),
    GOLD("Amber Gold", 0xFFFFB300L),
    CORAL("Electric Coral", 0xFFFF3D71L),
    EMERALD("Emerald Green", 0xFF00E676L),
    VIOLET("Electric Violet", 0xFFB388FFL)
}
