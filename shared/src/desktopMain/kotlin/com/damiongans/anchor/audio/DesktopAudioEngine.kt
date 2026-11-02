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
 * Desktop (JVM) audio engine for the future Windows app (x64 and arm64), built on
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

    @Volatile private var running = false
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

    override fun selectAudioOutputDevice(deviceId: Int?) {
        preferredMixerIndex = deviceId?.takeIf { it >= 0 }
        val wasRunning = running
        if (wasRunning) stop()
        detectCurrentAudioDevice()
        if (wasRunning) start()
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

    @Synchronized
    override fun start() {
        if (running) return
        val dataLine = openLine() ?: return
        line = dataLine
        running = true
        detectCurrentAudioDevice()
        audioThread = Thread({ runAudioLoop(dataLine) }, "AnchorDesktopAudio").apply {
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

    @Synchronized
    override fun stop() {
        if (!running) return
        running = false
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

    override fun release() {
        stop()
    }

    /**
     * Desktop I/O loop: blocking writes of the shared sequencer's warmup dither and click chunks
     * to the line, with the hardware playhead fed back for playhead-anchored dispatch.
     */
    private fun runAudioLoop(dataLine: SourceDataLine) {
        try {
            val chunk = ShortArray(CHUNK_FRAMES)
            val bytes = ByteArray(CHUNK_FRAMES * BYTES_PER_FRAME)
            val warmupChunks = MetronomeSequencer.warmupChunkCount(sampleRate, CHUNK_FRAMES, minimumChunks = 2)

            syncCompensatorSettings()
            sequencer.prepare(sampleRate, warmupChunks.toLong() * CHUNK_FRAMES)
            val playhead = PlayheadEstimator(sampleRate, maxInterpolationFrames = CHUNK_FRAMES * 2L)

            fun playheadFrame(): Long = playhead.estimate(
                hardwareFrames = dataLine.longFramePosition,
                nowNanos = System.nanoTime(),
                totalWritten = sequencer.totalFramesWritten
            )

            // 1. Hardware warmup: >= 55 ms of +/-1 LSB dither before Beat 1 (first chunk primes the
            //    line buffer before start()).
            MetronomeSequencer.fillWarmupDither(chunk, CHUNK_FRAMES)
            toLittleEndian(chunk, bytes)
            for (i in 0 until warmupChunks) {
                if (!running) return
                dataLine.write(bytes, 0, bytes.size)
                sequencer.advanceWarmup(CHUNK_FRAMES)
                if (i == 0) {
                    dataLine.start()
                    playhead.reset(System.nanoTime())
                } else {
                    sequencer.processPlayhead(playheadFrame(), emitPhase = false)
                }
            }

            // 2. Anchor Beat 1 at the exact post-warmup frame
            sequencer.startFirstBeat()

            // 3. Continuous synthesis & playhead-synchronized dispatch
            while (running) {
                sequencer.renderChunk(chunk, CHUNK_FRAMES)
                toLittleEndian(chunk, bytes)
                if (dataLine.write(bytes, 0, bytes.size) <= 0 && !running) break
                sequencer.processPlayhead(playheadFrame())
            }
        } catch (_: Throwable) {
            // Line closed or device removed while playing; stop() / route detection handle state.
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
