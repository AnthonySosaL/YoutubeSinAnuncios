package com.vancedclean.app.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.core.app.ServiceCompat
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vancedclean.app.MainActivity
import com.vancedclean.app.R

class BackgroundPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    /** Reproductor al que está enganchada la sesión (para detectar cuando la app crea otro). */
    private var sessionPlayer: Player? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (!ensureSession()) stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // La app pudo crear un reproductor nuevo (otro video, música, descarga): engancharse a él.
        if (!ensureSession()) {
            shutdown()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** Crea (o rehace) la sesión con el reproductor actual. false si ya no hay reproductor. */
    private fun ensureSession(): Boolean {
        val exoPlayer = VideoPlayerManager.getPlayer() ?: return false
        if (sessionPlayer === exoPlayer && mediaSession != null) return true
        mediaSession?.let {
            removeSession(it)
            it.release()
        }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val session = MediaSession.Builder(this, SkipPlayer(exoPlayer))
            .setId("zaza-" + System.identityHashCode(exoPlayer))
            .setSessionActivity(sessionActivity)
            .setCallback(SessionCallback())
            .build()

        session.setCustomLayout(
            ImmutableList.of(
                CommandButton.Builder()
                    .setDisplayName("Cerrar")
                    .setIconResId(R.drawable.ic_notif_close)
                    .setSessionCommand(CLOSE_COMMAND)
                    .build()
            )
        )

        // Sin esto la sesión solo se registra cuando un MediaController externo se
        // conecta (onGetSession), así que la notificación nunca aparecía con la app abierta.
        addSession(session)
        mediaSession = session
        sessionPlayer = exoPlayer
        return true
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // La app se cerró desde recientes: se acaba todo y se quita la notificación.
        PlayerSheet.close()
        shutdown()
    }

    override fun onDestroy() {
        releaseSession()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun releaseSession() {
        mediaSession?.let {
            removeSession(it)
            it.release()
        }
        mediaSession = null
        sessionPlayer = null
    }

    /**
     * Quita la notificación y termina el servicio. Hace falta soltar la sesión: mientras exista,
     * el sistema mantiene el servicio conectado y stopSelf() solo no lo cierra.
     */
    fun shutdown() {
        releaseSession()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        runCatching {
            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager).cancel(DEFAULT_NOTIFICATION_ID)
        }
        stopSelf()
    }

    /** X de la notificación: para la música o el video, quita la notificación y cierra el servicio. */
    private fun closePlayback() {
        PlayerSheet.close()
        VideoPlayerManager.release()
        shutdown()
    }

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val base = super.onConnect(session, controller)
            val sessionCommands = base.availableSessionCommands
                .buildUpon()
                .add(CLOSE_COMMAND)
                .build()
            return MediaSession.ConnectionResult.accept(
                sessionCommands,
                base.availablePlayerCommands
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_CLOSE) {
                closePlayback()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private class SkipPlayer(player: Player) : ForwardingPlayer(player) {

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands()
                .buildUpon()
                .addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS)
                .build()

        override fun isCommandAvailable(command: Int): Boolean =
            command == Player.COMMAND_SEEK_TO_NEXT ||
                command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                super.isCommandAvailable(command)

        override fun seekToNext() {
            if (hasNextMediaItem()) {
                super.seekToNext()
                return
            }
            val target = currentPosition + SKIP_INTERVAL_MS
            val total = duration
            seekTo(if (total == C.TIME_UNSET) target else target.coerceAtMost(total))
        }

        override fun seekToPrevious() {
            if (hasPreviousMediaItem()) {
                super.seekToPrevious()
                return
            }
            seekTo((currentPosition - SKIP_INTERVAL_MS).coerceAtLeast(0L))
        }
    }

    companion object {
        /** Servicio en marcha (para cerrarlo cuando la app suelta el reproductor). */
        @Volatile
        var instance: BackgroundPlaybackService? = null
            private set

        /** Id de la notificación de Media3 (DefaultMediaNotificationProvider). */
        private const val DEFAULT_NOTIFICATION_ID = 1001
        private const val SKIP_INTERVAL_MS = 10_000L
        private const val ACTION_CLOSE = "com.vancedclean.app.action.CLOSE"
        private val CLOSE_COMMAND = SessionCommand(ACTION_CLOSE, Bundle.EMPTY)
    }
}
