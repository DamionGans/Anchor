package com.damiongans.pulse.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.damiongans.pulse.MainActivity
import com.damiongans.pulse.R
import com.damiongans.pulse.model.TempoMarking
import com.damiongans.pulse.repository.SharedPreferencesSettingsRepository

/**
 * Foreground service of type mediaPlayback that keeps the Pulse Metronome audio engine
 * running without OS throttling when minimized or when the screen is off, and provides
 * multimedia notification drawer controls (Play/Pause, Tempo -/+, and live BPM readout).
 */
class MetronomePlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSession? = null

    private var currentBpm: Int = 120
    private var currentBeats: Int = 4
    private var currentDenominator: Int = 4
    private var currentIsPlaying: Boolean = false

    override fun onCreate() {
        super.onCreate()
        val settings = SharedPreferencesSettingsRepository(this)
        currentBpm = settings.getBpm()
        currentBeats = settings.getTotalBeats()
        currentDenominator = if (settings.getSubdivision() == com.damiongans.pulse.model.Subdivision.EIGHTH) 8 else 4

        createNotificationChannel()
        initMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS_NOTIFICATION -> {
                if (currentIsPlaying) {
                    handlePauseRequest()
                }
                releaseWakeLock()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_STOP_PLAYBACK, ACTION_PAUSE_PLAYBACK -> {
                handlePauseRequest()
                return START_STICKY
            }

            ACTION_PLAY_PLAYBACK -> {
                handlePlayRequest()
                return START_STICKY
            }

            ACTION_TOGGLE_PLAY_PAUSE -> {
                handleTogglePlayPauseRequest()
                return START_STICKY
            }

            ACTION_DECREASE_TEMPO -> {
                val delta = intent.getIntExtra(EXTRA_BPM_DELTA, -1)
                handleAdjustBpmRequest(delta)
                return START_STICKY
            }

            ACTION_INCREASE_TEMPO -> {
                val delta = intent.getIntExtra(EXTRA_BPM_DELTA, 1)
                handleAdjustBpmRequest(delta)
                return START_STICKY
            }

            else -> {
                if (intent != null) {
                    currentBpm = intent.getIntExtra(EXTRA_BPM, currentBpm).coerceIn(20, 300)
                    currentBeats = intent.getIntExtra(EXTRA_BEATS, currentBeats).coerceIn(1, 16)
                    currentDenominator = intent.getIntExtra(EXTRA_DENOMINATOR, currentDenominator)
                    if (intent.hasExtra(EXTRA_IS_PLAYING)) {
                        currentIsPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, currentIsPlaying)
                    } else {
                        currentIsPlaying = true
                    }
                }
                applyPlaybackAndNotificationState()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {
        } finally {
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initMediaSession() {
        try {
            mediaSession = MediaSession(this, "PulseMetronomeMediaSession").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        handlePlayRequest()
                    }

                    override fun onPause() {
                        handlePauseRequest()
                    }

                    override fun onStop() {
                        handlePauseRequest()
                    }

                    override fun onSkipToPrevious() {
                        handleAdjustBpmRequest(-1)
                    }

                    override fun onSkipToNext() {
                        handleAdjustBpmRequest(1)
                    }

                    override fun onCustomAction(action: String, extras: Bundle?) {
                        when (action) {
                            ACTION_DECREASE_TEMPO -> handleAdjustBpmRequest(-1)
                            ACTION_INCREASE_TEMPO -> handleAdjustBpmRequest(1)
                            ACTION_TOGGLE_PLAY_PAUSE -> handleTogglePlayPauseRequest()
                        }
                    }
                })
                isActive = true
            }
        } catch (_: Exception) {
            // Safe fallback in headless unit test environments
        }
    }

    private fun handlePlayRequest() {
        val callback = onPlayRequested ?: onTogglePlayPauseRequested
        if (callback != null) {
            if (onPlayRequested != null) {
                onPlayRequested?.invoke()
            } else if (!currentIsPlaying) {
                onTogglePlayPauseRequested?.invoke()
            }
        } else {
            currentIsPlaying = true
            applyPlaybackAndNotificationState()
        }
    }

    private fun handlePauseRequest() {
        val callback = onPauseRequested ?: onStopRequested ?: onTogglePlayPauseRequested
        if (callback != null) {
            when {
                onPauseRequested != null -> onPauseRequested?.invoke()
                onStopRequested != null -> onStopRequested?.invoke()
                currentIsPlaying -> onTogglePlayPauseRequested?.invoke()
            }
        } else {
            currentIsPlaying = false
            applyPlaybackAndNotificationState()
        }
    }

    private fun handleTogglePlayPauseRequest() {
        when {
            onTogglePlayPauseRequested != null -> onTogglePlayPauseRequested?.invoke()
            currentIsPlaying -> handlePauseRequest()
            else -> handlePlayRequest()
        }
    }

    private fun handleAdjustBpmRequest(delta: Int) {
        val callback = onAdjustBpmRequested
        if (callback != null) {
            callback.invoke(delta)
        } else {
            currentBpm = (currentBpm + delta).coerceIn(20, 300)
            try {
                SharedPreferencesSettingsRepository(this).setBpm(currentBpm)
            } catch (_: Exception) {
            }
            applyPlaybackAndNotificationState()
        }
    }

    private fun applyPlaybackAndNotificationState() {
        if (currentIsPlaying) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }

        updateMediaSessionState()
        val notification = buildNotification(
            bpm = currentBpm,
            beats = currentBeats,
            denominator = currentDenominator,
            isPlaying = currentIsPlaying
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            try {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.notify(NOTIFICATION_ID, notification)
            } catch (_: Exception) {
            }
        }
    }

    private fun updateMediaSessionState() {
        val session = mediaSession ?: return
        try {
            val tempoMarking = TempoMarking.forBpm(currentBpm)
            val statusLabel = if (currentIsPlaying) "Playing" else "Paused"
            val title = "$currentBpm BPM • $tempoMarking"
            val subtitle = "$statusLabel • $currentBeats/$currentDenominator Time"

            val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Pulse Metronome")
                .build()
            session.setMetadata(metadata)

            val playbackState = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP
                )
                .setState(
                    if (currentIsPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (currentIsPlaying) 1.0f else 0.0f,
                    SystemClock.elapsedRealtime()
                )
                .addCustomAction(
                    PlaybackState.CustomAction.Builder(
                        ACTION_DECREASE_TEMPO,
                        "-1 BPM",
                        R.drawable.ic_notif_tempo_minus
                    ).build()
                )
                .addCustomAction(
                    PlaybackState.CustomAction.Builder(
                        ACTION_INCREASE_TEMPO,
                        "+1 BPM",
                        R.drawable.ic_notif_tempo_plus
                    ).build()
                )
                .build()
            session.setPlaybackState(playbackState)
            session.isActive = true
        } catch (_: Exception) {
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PulseMetronome::PlaybackServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
            // Safe fallback if permission or service unavailable in test environment
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        } finally {
            wakeLock = null
        }
    }

    private fun stopForegroundCompat() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Metronome Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active metronome playback and tempo controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    internal fun buildNotification(
        bpm: Int,
        beats: Int,
        denominator: Int = 4,
        isPlaying: Boolean = true
    ): Notification {
        val tempoMarking = TempoMarking.forBpm(bpm)
        val statusLabel = if (isPlaying) "Playing" else "Paused"
        val title = "$bpm BPM • $tempoMarking"
        val subtitle = "$statusLabel • $beats/$denominator Time"

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val decreaseIntent = Intent(this, MetronomePlaybackService::class.java).apply {
            action = ACTION_DECREASE_TEMPO
            putExtra(EXTRA_BPM_DELTA, -1)
        }
        val decreasePendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_DECREASE_TEMPO,
            decreaseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePlayPauseIntent = Intent(this, MetronomePlaybackService::class.java).apply {
            action = ACTION_TOGGLE_PLAY_PAUSE
        }
        val togglePlayPausePendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_TOGGLE_PLAY_PAUSE,
            togglePlayPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val increaseIntent = Intent(this, MetronomePlaybackService::class.java).apply {
            action = ACTION_INCREASE_TEMPO
            putExtra(EXTRA_BPM_DELTA, 1)
        }
        val increasePendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_INCREASE_TEMPO,
            increaseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, MetronomePlaybackService::class.java).apply {
            action = ACTION_DISMISS_NOTIFICATION
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_DISMISS,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val decreaseAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_notif_tempo_minus),
            "-1 BPM",
            decreasePendingIntent
        ).build()

        val playPauseAction = Notification.Action.Builder(
            Icon.createWithResource(
                this,
                if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
            ),
            if (isPlaying) "Pause" else "Play",
            togglePlayPausePendingIntent
        ).build()

        val increaseAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_notif_tempo_plus),
            "+1 BPM",
            increasePendingIntent
        ).build()

        val mediaStyle = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        mediaSession?.sessionToken?.let { token ->
            mediaStyle.setMediaSession(token)
        }

        builder
            .setSmallIcon(R.drawable.ic_notif_metronome)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText("Pulse Metronome")
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle)
            .addAction(decreaseAction)
            .addAction(playPauseAction)
            .addAction(increaseAction)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    companion object {
        internal const val CHANNEL_ID = "pulse_metronome_playback"
        internal const val NOTIFICATION_ID = 1001

        private const val REQUEST_CODE_OPEN_APP = 100
        private const val REQUEST_CODE_DECREASE_TEMPO = 101
        private const val REQUEST_CODE_TOGGLE_PLAY_PAUSE = 102
        private const val REQUEST_CODE_INCREASE_TEMPO = 103
        private const val REQUEST_CODE_DISMISS = 104

        const val ACTION_SYNC_STATE = "com.damiongans.pulse.action.SYNC_STATE"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.damiongans.pulse.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_PLAY_PLAYBACK = "com.damiongans.pulse.action.PLAY_PLAYBACK"
        const val ACTION_PAUSE_PLAYBACK = "com.damiongans.pulse.action.PAUSE_PLAYBACK"
        const val ACTION_STOP_PLAYBACK = "com.damiongans.pulse.action.STOP_PLAYBACK"
        const val ACTION_DISMISS_NOTIFICATION = "com.damiongans.pulse.action.DISMISS_NOTIFICATION"
        const val ACTION_DECREASE_TEMPO = "com.damiongans.pulse.action.DECREASE_TEMPO"
        const val ACTION_INCREASE_TEMPO = "com.damiongans.pulse.action.INCREASE_TEMPO"

        const val EXTRA_BPM = "extra_bpm"
        const val EXTRA_BEATS = "extra_beats"
        const val EXTRA_DENOMINATOR = "extra_denominator"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_BPM_DELTA = "extra_bpm_delta"

        @Volatile
        var onPlayRequested: (() -> Unit)? = null

        @Volatile
        var onPauseRequested: (() -> Unit)? = null

        @Volatile
        var onTogglePlayPauseRequested: (() -> Unit)? = null

        @Volatile
        var onAdjustBpmRequested: ((Int) -> Unit)? = null

        @Volatile
        var onStopRequested: (() -> Unit)? = null

        fun startOrUpdate(
            context: Context,
            bpm: Int,
            beats: Int,
            denominator: Int = 4,
            isPlaying: Boolean = true
        ) {
            try {
                val intent = Intent(context, MetronomePlaybackService::class.java).apply {
                    action = ACTION_SYNC_STATE
                    putExtra(EXTRA_BPM, bpm)
                    putExtra(EXTRA_BEATS, beats)
                    putExtra(EXTRA_DENOMINATOR, denominator)
                    putExtra(EXTRA_IS_PLAYING, isPlaying)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // Safe fallback if background service start is restricted in test environment
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, MetronomePlaybackService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
