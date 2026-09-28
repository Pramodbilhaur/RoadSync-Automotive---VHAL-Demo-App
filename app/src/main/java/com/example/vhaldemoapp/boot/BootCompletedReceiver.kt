package com.example.vhaldemoapp.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.vhaldemoapp.service.CarPropertyMonitoringService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startService(Intent(context, CarPropertyMonitoringService::class.java))
        }
    }
}
