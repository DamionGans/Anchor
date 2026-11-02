package com.damiongans.anchor.playback

import com.damiongans.anchor.model.TempoMarking

/**
 * Snapshot of what the system media surfaces (Android notification / MediaSession,
 * iOS Now Playing / Lock Screen) should display.
 */
data class PlaybackSessionState(
    val bpm: Int,
    val beats: Int,
    val denominator: Int,
    val isPlaying: Boolean
) {
    /** e.g. "120 BPM • Allegro" */
    val title: String
        get() = "$bpm BPM • ${TempoMarking.forBpm(bpm)}"

    /** e.g. "Playing • 4/4 Time" */
    val subtitle: String
        get() = "${if (isPlaying) "Playing" else "Paused"} • $beats/$denominator Time"
}

/**
 * Transport commands raised by system media controls.
 */
interface PlaybackCommandListener {
    fun onPlay()
    fun onPause()
    fun onTogglePlayPause()
    fun onAdjustBpm(delta: Int)
    fun onStop()
}

/**
 * Platform-agnostic contract for background playback / system media controls.
 * Android: foreground service + MediaSession notification. iOS: MPNowPlayingInfoCenter +
 * MPRemoteCommandCenter.
 */
interface IPlaybackSession {
    var commandListener: PlaybackCommandListener?
    fun update(state: PlaybackSessionState)
    fun release()
}

/** No-op session (unit tests or platforms without system media controls). */
class NoOpPlaybackSession : IPlaybackSession {
    override var commandListener: PlaybackCommandListener? = null
    var lastState: PlaybackSessionState? = null
        private set
    var updateCount: Int = 0
        private set

    override fun update(state: PlaybackSessionState) {
        lastState = state
        updateCount++
    }

    override fun release() {
        commandListener = null
    }
}
