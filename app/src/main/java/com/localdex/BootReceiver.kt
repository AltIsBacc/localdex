package com.localdex

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.isActive(context)) {
            ContextCompat.startForegroundService(context, Intent(context, DexService::class.java))
        }
    }
}
