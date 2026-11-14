package com.localdex

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DexService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lock = Mutex()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }

        when (intent?.action) {
            ACTION_ENABLE -> scope.launch { enable(intent.getStringExtra(EXTRA_SPEC).orEmpty()) }
            ACTION_SWITCH_BACK -> scope.launch { switchBack() }
            else -> scope.launch { resume() }
        }
        return START_STICKY
    }

    private suspend fun enable(spec: String) = lock.withLock {
        DexMode.enable(this, spec).onFailure {
            toast("Could not enable DeX: ${it.message}")
            finish()
        }
    }

    private suspend fun switchBack() = lock.withLock {
        DexMode.disable(this)
            .onSuccess { finish() }
            .onFailure { toast("Could not switch back: ${it.message}") }
    }

    private suspend fun resume() = lock.withLock {
        if (Prefs.isActive(this)) DexMode.reassert(this) else finish()
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun buildNotification(): Notification {
        val switchBack = PendingIntent.getService(
            this,
            1,
            Intent(this, DexService::class.java).setAction(ACTION_SWITCH_BACK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("DeX is active on the main display")
            .setContentText("Tap Switch back to restore the normal screen")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_revert, "Switch back", switchBack)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DeX session",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while DeX is running on the main display"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "dex_session"
        private const val NOTIFICATION_ID = 4001
        private const val EXTRA_SPEC = "spec"

        const val ACTION_ENABLE = "com.localdex.ENABLE_DEX"
        const val ACTION_SWITCH_BACK = "com.localdex.SWITCH_BACK"

        fun enable(context: Context, spec: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DexService::class.java)
                    .setAction(ACTION_ENABLE)
                    .putExtra(EXTRA_SPEC, spec)
            )
        }

        fun switchBack(context: Context) {
            context.startService(Intent(context, DexService::class.java).setAction(ACTION_SWITCH_BACK))
        }
    }
}
