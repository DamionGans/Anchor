package com.damiongans.anchor.service

import android.content.Context
import com.damiongans.anchor.playback.IPlaybackSession
import com.damiongans.anchor.playback.PlaybackCommandListener
import com.damiongans.anchor.playback.PlaybackSessionState

/**
 * Android [IPlaybackSession]: bridges the shared controller to [MetronomePlaybackService]
 * (foreground `mediaPlayback` service, MediaSession, and multimedia notification).
 */
class AndroidPlaybackSession(context: Context) : IPlaybackSession {

    private val appContext = context.applicationContext ?: context

    override var commandListener: PlaybackCommandListener? = null
        set(value) {
            field = value
            if (value != null) {
                MetronomePlaybackService.onPlayRequested = { value.onPlay() }
                MetronomePlaybackService.onPauseRequested = { value.onPause() }
                MetronomePlaybackService.onTogglePlayPauseRequested = { value.onTogglePlayPause() }
                MetronomePlaybackService.onAdjustBpmRequested = { delta -> value.onAdjustBpm(delta) }
                MetronomePlaybackService.onStopRequested = { value.onStop() }
            } else {
                clearServiceCallbacks()
            }
        }

    override fun update(state: PlaybackSessionState) {
        MetronomePlaybackService.startOrUpdate(
            context = appContext,
            bpm = state.bpm,
            beats = state.beats,
            denominator = state.denominator,
            isPlaying = state.isPlaying
        )
    }

    override fun release() {
        clearServiceCallbacks()
        MetronomePlaybackService.stop(appContext)
    }

    private fun clearServiceCallbacks() {
        MetronomePlaybackService.onPlayRequested = null
        MetronomePlaybackService.onPauseRequested = null
        MetronomePlaybackService.onTogglePlayPauseRequested = null
        MetronomePlaybackService.onAdjustBpmRequested = null
        MetronomePlaybackService.onStopRequested = null
    }
}
