package com.damiongans.anchor.audio

import com.damiongans.anchor.model.AudioDeviceType
import com.damiongans.anchor.model.AudioOutputDevice
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.Volatile

/**
 * Desktop (JVM) audio engine for the Windows app (x64 and arm64), built on
 * `javax.sound.sampled.SourceDataLine`.
 *
 * Mirrors the Android engine: all synthesis, warmup, sequencing, and playhead-anchored delay
 * compensation come from the shared [BaseMetronomeAudioEngine] / [MetronomeSequencer]. This class
 * only owns the JVM I/O: a high-priority thread doing blocking line writes, the hardware playhead
 * (`SourceDataLine.longFramePosition`, 64-bit), and output mixer discovery and selection.
 *
 * Without an audio device (e.g. CI runners), the route list contains only the system default
 * output and [start] returns without playing; nothing throws.
 */
class DesktopAudioEngine(
    initialSampleRate: Int = DEFAULT_SAMPLE_RATE
) : BaseMetronomeAudioEngine(initialSampleRate) {

    /** True while a line + output thread exist (playing or in standby). */
    @Volatile private var outputActive = false
    /** Metronome playback requested (start() .. stop()). Read by the output thread. */
    @Volatile private var playRequested = false
    /** Keep the line open between plays (app window open) for an instant first beat. */
    @Volatile private var standbyRequested = false
    /** Incremented by every start(), so a fast stop()+start() still restarts at Beat 1. */
    @Volatile private var playGeneration = 0
    @Volatile private var line: SourceDataLine? = null
    @Volatile private var preferredMixerIndex: Int? = null
    private var audioThread: Thread? = null

    init {
        detectCurrentAudioDevice()
    }

    private fun lineFormat(): AudioFormat =
        AudioFormat(sampleRate.toFloat(), 16, 1, /* signed = */ true, /* bigEndian = */ false)

    private fun lineInfo(): DataLine.Info = DataLine.Info(SourceDataLine::class.java, lineFormat())

    /** Mixers that can play our format, keyed by their index in `AudioSystem.getMixerInfo()`. */
    private fun outputMixers(): List<Pair<Int, Mixer.Info>> = try {
        val info = lineInfo()
        AudioSystem.getMixerInfo().withIndex()
            .filter { (_, mixerInfo) ->
                try {
                    AudioSystem.getMixer(mixerInfo).isLineSupported(info)
                } catch (_: Throwable) {
                    false
                }
            }
            .map { it.index to it.value }
    } catch (_: Throwable) {
        emptyList()
    }

    override fun detectCurrentAudioDevice() {
        val systemDefault = AudioOutputDevice(
            id = AudioRouteSelector.SPEAKER_DEVICE_ID,
            deviceType = AudioDeviceType.SPEAKER,
            name = SYSTEM_DEFAULT_NAME
        )
        val mixers = outputMixers()
            .filterNot { (_, info) -> info.name.contains(PRIMARY_DRIVER_NAME, ignoreCase = true) }
            .map { (index, info) -> AudioOutputDevice(index, classifyMixer(info.name), info.name) }

        val sorted = AudioRouteSelector.normalize(listOf(systemDefault) + mixers)
        val preferred = preferredMixerIndex
        if (preferred != null && sorted.none { it.id == preferred }) {
            preferredMixerIndex = null // selected device was disconnected
        }
        val active = AudioRouteSelector.selectActive(
            sortedDevices = sorted,
            preferredDeviceId = preferredMixerIndex,
            routedDevice = if (preferredMixerIndex == null) systemDefault else null
        )
        val bufferFrames = line?.bufferSize?.div(BYTES_PER_FRAME) ?: (CHUNK_FRAMES * BUFFER_CHUNKS)
        publishAudioRoute(active, sorted, bufferFrames, isProAudioSupported = false)
    }

    @Synchronized
    override fun selectAudioOutputDevice(deviceId: Int?) {
        preferredMixerIndex = deviceId?.takeIf { it >= 0 }
        val wasOpen = outputActive
        if (wasOpen) closeOutput()
        detectCurrentAudioDevice()
        // Reopen on the new mixer; a requested playback restarts there at Beat 1.
        if (wasOpen) openOutputIfNeeded()
    }

    private fun classifyMixer(name: String): AudioDeviceType {
        val n = name.lowercase()
        return when {
            BLUETOOTH_HINTS.any { it in n } -> AudioDeviceType.BLUETOOTH
            "usb" in n -> AudioDeviceType.USB_AUDIO
            "headphone" in n || "line out" in n -> AudioDeviceType.WIRED_HEADPHONES
            "speaker" in n -> AudioDeviceType.SPEAKER
            else -> AudioDeviceType.OTHER
        }
    }

    /**
     * Output standby: while enabled (app window open) the line stays open and keeps playing
     * inaudible dither, so [start] places Beat 1 in the next chunk instead of opening the line and
     * running the cold-start warmup. Disabling it closes the line unless the metronome is playing.
     */
    @Synchronized
    override fun setOutputStandby(enabled: Boolean) {
        standbyRequested = enabled
        if (enabled) {
            openOutputIfNeeded()
        } else if (!playRequested) {
            closeOutput()
        }
    }

    @Synchronized
    override fun start() {
        if (playRequested) return
        playGeneration++
        playRequested = true
        openOutputIfNeeded()
        if (!outputActive) playRequested = false // no usable output device
    }

    @Synchronized
    override fun stop() {
        if (!playRequested) return
        playRequested = false
        if (!standbyRequested) closeOutput()
    }

    @Synchronized
    override fun release() {
        standbyRequested = false
        playRequested = false
        closeOutput()
    }

    private fun openOutputIfNeeded() {
        if (outputActive) return
        val dataLine = openLine() ?: return
        line = dataLine
        outputActive = true
        detectCurrentAudioDevice()
        audioThread = Thread({ runOutputLoop(dataLine) }, "AnchorDesktopAudio").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    private fun openLine(): SourceDataLine? = try {
        val info = lineInfo()
        val mixerIndex = preferredMixerIndex
        val dataLine = if (mixerIndex != null) {
            AudioSystem.getMixer(AudioSystem.getMixerInfo()[mixerIndex]).getLine(info) as SourceDataLine
        } else {
            AudioSystem.getLine(info) as SourceDataLine
        }
        dataLine.open(lineFormat(), CHUNK_FRAMES * BUFFER_CHUNKS * BYTES_PER_FRAME)
        dataLine
    } catch (_: Throwable) {
        null // no usable output device
    }

    private fun closeOutput() {
        if (!outputActive) return
        outputActive = false
        val dataLine = line
        line = null
        try {
            dataLine?.stop()
            dataLine?.flush()
            dataLine?.close()
        } catch (_: Throwable) {
        }
        audioThread?.join(THREAD_JOIN_TIMEOUT_MS)
        audioThread = null
    }

    /**
     * Desktop I/O loop for the lifetime of one line: inaudible dither while idle (standby), the
     * shared sequencer's clicks while playing, with the hardware playhead fed back for
     * playhead-anchored dispatch. [outputFrames] counts every frame written to the line, the
     * same timeline as `longFramePosition`; playback aligns the sequencer to it.
     */
    private fun runOutputLoop(dataLine: SourceDataLine) {
        try {
            val chunk = ShortArray(CHUNK_FRAMES)
            val bytes = ByteArray(CHUNK_FRAMES * BYTES_PER_FRAME)
            val ditherBytes = ByteArray(CHUNK_FRAMES * BYTES_PER_FRAME)
            ShortArray(CHUNK_FRAMES).also {
                MetronomeSequencer.fillWarmupDither(it, CHUNK_FRAMES)
                toLittleEndian(it, ditherBytes)
            }
            // Cold-start warmup target: >= 55 ms of dither before Beat 1 (only the part the line
            // has not already played is added on a start).
            val warmupTargetFrames =
                MetronomeSequencer.warmupChunkCount(sampleRate, CHUNK_FRAMES, minimumChunks = 2).toLong() * CHUNK_FRAMES
            val playhead = PlayheadEstimator(sampleRate, maxInterpolationFrames = CHUNK_FRAMES * 2L)
            var outputFrames = 0L
            var playing = false
            var playingGeneration = -1

            fun write(data: ByteArray): Boolean {
                if (!outputActive) return false
                val written = dataLine.write(data, 0, data.size)
                if (written > 0) outputFrames += written / BYTES_PER_FRAME
                return written > 0 && outputActive
            }

            fun playheadFrame(): Long = playhead.estimate(
                hardwareFrames = dataLine.longFramePosition,
                nowNanos = System.nanoTime(),
                totalWritten = outputFrames
            )

            // Prime the line buffer, then start the hardware clock.
            if (!write(ditherBytes)) return
            dataLine.start()
            playhead.reset(System.nanoTime())

            while (outputActive) {
                val generation = playGeneration
                if (playRequested && (!playing || generation != playingGeneration)) {
                    val extraChunks = ((warmupTargetFrames - outputFrames).coerceAtLeast(0L) + CHUNK_FRAMES - 1) / CHUNK_FRAMES
                    syncCompensatorSettings()
                    sequencer.prepare(sampleRate, outputFrames + extraChunks * CHUNK_FRAMES)
                    sequencer.alignToOutputFrame(outputFrames)
                    var warmupOk = true
                    for (i in 0 until extraChunks) {
                        if (!write(ditherBytes)) return
                        if (!playRequested) {
                            warmupOk = false
                            break
                        }
                        sequencer.advanceWarmup(CHUNK_FRAMES)
                        sequencer.processPlayhead(playheadFrame(), emitPhase = false)
                    }
                    if (!warmupOk) {
                        playing = false
                        continue
                    }
                    // Anchor Beat 1 at the exact next frame
                    sequencer.startFirstBeat()
                    playing = true
                    playingGeneration = generation
                } else if (!playRequested && playing) {
                    playing = false
                }

                if (playing) {
                    sequencer.renderChunk(chunk, CHUNK_FRAMES)
                    toLittleEndian(chunk, bytes)
                    if (!write(bytes)) break
                    sequencer.processPlayhead(playheadFrame())
                } else {
                    // Standby: keep the output path awake with inaudible dither
                    if (!write(ditherBytes)) break
                }
            }
        } catch (_: Throwable) {
            // Line closed or device removed; closeOutput() / route detection own the state.
        }
    }

    private fun toLittleEndian(samples: ShortArray, out: ByteArray) {
        for (i in samples.indices) {
            val value = samples[i].toInt()
            out[2 * i] = (value and 0xFF).toByte()
            out[2 * i + 1] = ((value shr 8) and 0xFF).toByte()
        }
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 48000
        const val SYSTEM_DEFAULT_NAME = "System Default Output"
        private const val PRIMARY_DRIVER_NAME = "Primary Sound Driver" // Windows alias of the default
        private const val CHUNK_FRAMES = 256
        private const val BUFFER_CHUNKS = 4
        private const val BYTES_PER_FRAME = 2
        private const val THREAD_JOIN_TIMEOUT_MS = 500L
        private val BLUETOOTH_HINTS = listOf("bluetooth", "hands-free", "headset", "airpods", "buds")
    }
}
