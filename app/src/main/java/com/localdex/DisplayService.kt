package com.localdex

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DisplayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Virtual display", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val config = DisplaySession.config.value
        startForeground(NOTIFICATION_ID, notification(config))
        if (intent?.action == ACTION_CLOSE) DisplaySession.stop()
        if (DisplaySession.config.value == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (watcher == null) {
            watcher = scope.launch {
                DisplaySession.config.first { it == null }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(config: DisplaySession.Config?) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Virtual display running")
        .setContentText(
            if (config == null) "Tap to return" else "${config.width}×${config.height} · tap to return"
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, DisplayActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .addAction(
            0,
            "Close",
            PendingIntent.getService(
                this,
                1,
                Intent(this, DisplayService::class.java).setAction(ACTION_CLOSE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    companion object {
        private const val CHANNEL_ID = "display"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_CLOSE = "com.localdex.action.CLOSE"

        fun open(context: Context) {
            DisplaySession.start(context)
            ContextCompat.startForegroundService(context, Intent(context, DisplayService::class.java))
        }
    }
}
