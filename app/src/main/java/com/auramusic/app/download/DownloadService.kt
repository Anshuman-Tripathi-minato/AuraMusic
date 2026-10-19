package com.auramusic.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.auramusic.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Foreground service that keeps downloads alive in the background and shows progress. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        ensureChannel()
        val ok = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, build("Preparing download…", -1f),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        }.isSuccess
        if (!ok) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            scope.launch {
                DownloadCenter.tasks.collectLatest { tasks ->
                    delay(400) // throttle notification updates
                    val running = tasks.firstOrNull { it.status == DownloadCenter.Status.RUNNING }
                    val queued = tasks.count { it.status == DownloadCenter.Status.QUEUED }
                    if (running == null && queued == 0) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf(lastStartId)
                        return@collectLatest
                    }
                    val stage = when (running?.stage) {
                        DownloadStage.CONVERTING -> "Converting with FFmpeg"
                        DownloadStage.EMBEDDING -> "Adding cover art"
                        DownloadStage.SAVING -> "Saving"
                        DownloadStage.DOWNLOADING -> "Downloading"
                        else -> "Fetching"
                    }
                    val text = (running?.title ?: "Waiting…") + if (queued > 0) "  •  $queued queued" else ""
                    runCatching {
                        NotificationManagerCompat.from(this@DownloadService)
                            .notify(NOTIFICATION_ID, build(text, running?.percent ?: -1f, stage))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun build(text: String, percent: Float, title: String = "Aura Music download") = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText(text)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setSilent(true)
        .setProgress(100, percent.toInt().coerceIn(0, 100), percent < 0f || percent >= 100f)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 1,
                Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    companion object {
        private const val CHANNEL = "aura_downloads"
        private const val NOTIFICATION_ID = 4242
    }
}
