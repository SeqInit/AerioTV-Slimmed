package com.aeriotv.android.core.playback

import kotlinx.coroutines.flow.distinctUntilChanged
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aeriotv.android.MainActivity
import com.aeriotv.android.R
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Media3 [MediaSessionService] backing background audio (notification + remote
 * media keys + Bluetooth). The 5s foreground-start placeholder is posted from
 * onStartCommand: the explicit background-audio start path
 * (startForegroundService -> onStartCommand) satisfies the deadline, while a
 * controller that only BINDS the service never triggers a foreground
 * notification (which would also risk a ForegroundServiceStartNotAllowed crash
 * when bound from the background).
 *
 * The MediaSession wraps the SAME ExoPlayer instance mounted in
 * PersistentExoWindow, so the foreground PlayerScreen and the background
 * controls read/drive identical state.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class AerioMediaPlaybackService : MediaSessionService() {

    @Inject lateinit var exoHolder: AerioExoPlayerHolder

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Tap target for the foreground notification -> back into the app. */
    private val launchPi: PendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()

        val player = exoHolder.acquireOrCreate(this)

        // SkipIntervalsPlayer: seek back / forward from the notification, lock
        // screen, and Bluetooth follow Settings > Skip Intervals live (an
        // ExoPlayer's own increments are fixed when it is built).
        mediaSession = MediaSession.Builder(this, SkipIntervalsPlayer(player))
            .setCallback(SessionCallback())
            .setSessionActivity(launchPi)
            .build()

        // Let Media3 own the foreground notification so it shows the REAL
        // now-playing (channel name, programme, logo, play/pause/next) pulled
        // from the session's current MediaItem metadata. Pinned to the SAME
        // channel + notification id as our onStartCommand placeholder so the
        // rich notification REPLACES "Starting playback..." instead of leaving
        // it stuck (user report: the only sign the app was running was that
        // useless placeholder).
        // Radio Mode (Android#127): a Stop button on the media notification
        // while it is active (Play/Pause comes from the session itself).
        scope.launch {
            com.aeriotv.android.feature.player.RadioMode.state.collect { radio ->
                mediaSession?.setCustomLayout(
                    if (radio == null) emptyList() else listOf(
                        androidx.media3.session.CommandButton.Builder()
                            .setDisplayName("Stop")
                            .setIconResId(android.R.drawable.ic_menu_close_clear_cancel)
                            .setSessionCommand(RADIO_STOP)
                            .build(),
                    ),
                )
            }
        }
        // Radio Mode on a failed stream (Channel Unavailable): the
        // notification says so instead of the channel's "Live" line; a
        // recovered stream puts the normal line back.
        scope.launch {
            kotlinx.coroutines.flow.combine(
                com.aeriotv.android.feature.player.RadioMode.state,
                exoHolder.streamUnavailable,
                exoHolder.failedChannel,
                exoHolder.audioOnlyHolding,
            ) { radio, unavailable, failedId, holding ->
                when {
                    radio == null -> 0
                    unavailable || failedId != null -> 1
                    holding -> 2
                    else -> 0
                }
            }
                .distinctUntilChanged()
                .collect { status ->
                    if (com.aeriotv.android.feature.player.RadioMode.state.value == null) return@collect
                    val failed = status == 1
                    com.aeriotv.android.core.pip.PipState.nowPlayingSubtitle = when (status) {
                        1 -> com.aeriotv.android.feature.player.RADIO_UNAVAILABLE
                        2 -> com.aeriotv.android.feature.player.RADIO_BUFFERING
                        else -> ""
                    }
                    runCatching {
                        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                            .notify(NOTIF_ID, buildNowPlayingNotification())
                    }
                    android.util.Log.i(TAG, "[RADIO] notification ${if (failed) "Channel Unavailable" else if (status == 2) "Buffering" else "Live"}")
                }
        }
        setMediaNotificationProvider(
            androidx.media3.session.DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.app_name)
                .setNotificationId(NOTIF_ID)
                .build(),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent is the system re-creating a sticky service after process
        // death. There is nothing to resume (the player lived in the dead
        // process), and the app is usually in the background, where
        // startForeground() throws ForegroundServiceStartNotAllowedException
        // (JayK crash: "Unable to start service ... with null"). Stop quietly.
        if (intent == null) {
            val p = exoHolder.player
            val active = p != null && p.playWhenReady && p.currentMediaItem != null
            if (!active) {
                android.util.Log.i(TAG, "sticky restart with null intent and no playback; stopping")
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        // Explicit start (background-audio promotion from PlayerScreen) must
        // beat the API 31+ 5s foreground-start deadline. A controller that only
        // BINDS never reaches here -- no spurious foreground notification, no
        // FGS-start-not-allowed risk.
        return try {
            startForegroundCompat(buildNowPlayingNotification())
            super.onStartCommand(intent, flags, startId)
            // Never sticky: a system restart after process death has no player
            // to promote and would only hit the background FGS-start ban.
            START_NOT_STICKY
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) extends
            // IllegalStateException; Media3's own promotion can throw it too.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is android.app.ForegroundServiceStartNotAllowedException
            ) {
                android.util.Log.w(TAG, "startForeground not allowed from background; stopping", e)
                stopSelf(startId)
                START_NOT_STICKY
            } else {
                throw e
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            // Do NOT release the player here -- it's owned by
            // AerioExoPlayerHolder and may still be in use by PlayerScreen.
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /** Controller gate + the Radio Mode Stop command. */
    private inner class SessionCallback : MediaSession.Callback {

        // Only the app itself and system-level controllers (Assistant,
        // Bluetooth, media resumption -- all have MEDIA_CONTENT_CONTROL and
        // show as isTrusted) may drive playback. Any other third-party app is
        // rejected to prevent player-state hijack.
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            return if (controller.packageName == packageName || controller.isTrusted) {
                MediaSession.ConnectionResult.accept(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon().add(RADIO_STOP).build(),
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS,
                )
            } else {
                MediaSession.ConnectionResult.reject()
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<androidx.media3.session.SessionResult> {
            if (customCommand.customAction == RADIO_STOP.customAction) {
                com.aeriotv.android.feature.player.RadioMode.stop(this@AerioMediaPlaybackService, "notification")
                return com.google.common.util.concurrent.Futures.immediateFuture(
                    androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS),
                )
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ongoing notification while AerioTV plays audio in the background."
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    /** The live "Now Playing" notification: real channel + programme, tappable back into the app. Replaces the
     *  old static "Starting playback..." placeholder that stuck + did nothing. */
    private fun buildNowPlayingNotification(): Notification {
        val title = com.aeriotv.android.core.pip.PipState.nowPlayingTitle.ifBlank { "AerioTV" }
        val text = com.aeriotv.android.core.pip.PipState.nowPlayingSubtitle.ifBlank { "Live" }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(launchPi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Discord report (praveen12bnitt): live playback surfaced this
            // plain notification instead of the system media card. The rich
            // Media3 provider replaces it once the session player reports
            // media + state, but until then (and on any path where that swap
            // never fires) a NON-MediaStyle foreground notification is what
            // the OS shows - and it is not eligible for the Quick Settings
            // media carousel. Binding MediaStyle to the session token makes
            // even the placeholder render as a proper media card.
            .apply {
                mediaSession?.let {
                    setStyle(
                        androidx.media.app.NotificationCompat.MediaStyle()
                            .setMediaSession(it.sessionCompatToken),
                    )
                }
            }
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val TAG = "AerioPlaybackService"
        private const val CHANNEL_ID = "aeriotv_background_playback"
        private val RADIO_STOP = androidx.media3.session.SessionCommand("com.aeriotv.android.RADIO_STOP", android.os.Bundle.EMPTY)
        private const val NOTIF_ID = 0xAF

        fun startBackground(context: android.content.Context) {
            val intent = Intent(context, AerioMediaPlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, AerioMediaPlaybackService::class.java)
            context.stopService(intent)
        }
    }
}
