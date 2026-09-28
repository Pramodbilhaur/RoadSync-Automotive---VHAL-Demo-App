package com.example.vhaldemoapp.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.vhaldemoapp.data.VehiclePropertyRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CarPropertyMonitoringService : Service() {
    @Inject lateinit var repository: VehiclePropertyRepository

    override fun onCreate() {
        super.onCreate()
        repository.startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        repository.stopMonitoring()
        super.onDestroy()
    }
}
