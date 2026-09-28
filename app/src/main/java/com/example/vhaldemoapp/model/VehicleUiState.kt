package com.example.vhaldemoapp.model

import com.example.vhaldemoapp.data.CarInfoData

data class VehicleUiState(
    val isCarConnected: Boolean = false,
    val fanSpeed: Int? = 1,
    val maxFanSpeed: Int = 6,
    val isHvacPowerOn: Boolean = true,
    val isAcOn: Boolean = true,
    val isAutoClimateOn: Boolean = false,
    val hvacTemperatureC: Float? = 21.5f,
    val gearSelection: Int? = null,
    val carInfo: CarInfoData? = null,
    val errorMessage: String? = null,
    val speedKmh: Float? = null,
    val fuelLevelPercent: Float? = null
)
