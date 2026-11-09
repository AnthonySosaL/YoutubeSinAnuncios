package com.vancedclean.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.vancedclean.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Mantiene viva la app mientras descarga y muestra el progreso en una notificación. */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Descargas", NotificationManager.IMPORTANCE_LOW))
        }
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, build("Preparando descarga…", 0f),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
        scope.launch {
            DownloadQueue.tasks.collectLatest { tasks ->
                val running = tasks.firstOrNull { it.state == TaskState.DESCARGANDO || it.state == TaskState.CONVIRTIENDO }
                    ?: tasks.firstOrNull { it.state == TaskState.EN_COLA }
                if (running == null) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val pending = DownloadQueue.active
                    val text = (if (pending > 1) "($pending) " else "") + running.message.ifBlank { "En cola" }
                    nm.notify(NOTIFICATION_ID, build(running.title, running.progress, text))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun build(title: String, progress: Float, text: String = ""): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, (progress * 100).toInt(), progress <= 0f)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "descargas"
        private const val NOTIFICATION_ID = 4201

        /** El servicio se cierra solo cuando no quedan descargas activas. */
        fun refresh(context: Context) = Unit
    }
}
