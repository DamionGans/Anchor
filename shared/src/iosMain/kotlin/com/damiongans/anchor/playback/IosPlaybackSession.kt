package com.damiongans.anchor.playback

import platform.MediaPlayer.*

/**
 * iOS [IPlaybackSession]: Lock Screen / Control Center "Now Playing" info
 * (`"<BPM> BPM • <Marking>"` / `"Playing • 4/4 Time"`) and remote transport commands
 * (play, pause, toggle, stop, next track = +1 BPM, previous track = -1 BPM).
 * Must be created on the main thread.
 */
class IosPlaybackSession : IPlaybackSession {

    override var commandListener: PlaybackCommandListener? = null

    private val registeredTargets = mutableListOf<Pair<MPRemoteCommand, Any>>()

    init {
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        register(center.playCommand) { commandListener?.onPlay() }
        register(center.pauseCommand) { commandListener?.onPause() }
        register(center.togglePlayPauseCommand) { commandListener?.onTogglePlayPause() }
        register(center.stopCommand) { commandListener?.onStop() }
        register(center.nextTrackCommand) { commandListener?.onAdjustBpm(1) }
        register(center.previousTrackCommand) { commandListener?.onAdjustBpm(-1) }
    }

    private fun register(command: MPRemoteCommand, action: () -> Unit) {
        command.enabled = true
        val target = command.addTargetWithHandler { _ ->
            action()
            MPRemoteCommandHandlerStatusSuccess
        }
        registeredTargets.add(command to target)
    }

    override fun update(state: PlaybackSessionState) {
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = mapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to state.title,
            MPMediaItemPropertyArtist to state.subtitle,
            MPMediaItemPropertyAlbumTitle to "Anchor Metronome",
            MPNowPlayingInfoPropertyPlaybackRate to if (state.isPlaying) 1.0 else 0.0,
            MPNowPlayingInfoPropertyIsLiveStream to true
        )
    }

    override fun release() {
        for ((command, target) in registeredTargets) {
            command.removeTarget(target)
        }
        registeredTargets.clear()
        commandListener = null
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }
}
