package com.example.vhaldemoapp.data

import android.car.Car
import android.car.VehicleAreaSeat
import android.car.VehicleAreaType
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import com.example.vhaldemoapp.model.VehicleUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

@Singleton
class VehiclePropertyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutableUiState = MutableStateFlow(VehicleUiState())
    val uiState: StateFlow<VehicleUiState> = mutableUiState.asStateFlow()

    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> updateGear(value.value)
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> updateSpeed(value.value)
                VehiclePropertyIds.FUEL_LEVEL,
                VehiclePropertyIds.EV_BATTERY_LEVEL -> updateFuel(value.value)
                VehiclePropertyIds.HVAC_FAN_SPEED -> updateFanSpeed(value.value)
                VehiclePropertyIds.HVAC_POWER_ON -> updateHvacPower(value.value)
                VehiclePropertyIds.HVAC_AC_ON -> updateAcState(value.value)
                VehiclePropertyIds.HVAC_AUTO_ON -> updateAutoClimateState(value.value)
                VehiclePropertyIds.HVAC_TEMPERATURE_SET -> updateTemperature(value.value)
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            updateState {
                it.copy(errorMessage = "VHAL error for property $propertyId, area $areaId")
            }
        }
    }

    fun startMonitoring() {
        repositoryScope.launch { startMonitoringOnWorker() }
    }

    private fun startMonitoringOnWorker() {
        if (propertyManager != null) return

        try {
            car = Car.createCar(context)
            if (car?.isConnected != true) {
                updateState { it.copy(errorMessage = "Android Car Service is not connected") }
                return
            }

            propertyManager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            val manager = propertyManager ?: error("Car property service is unavailable")

            getVehicleInfo()

            listOf(
                VehiclePropertyIds.GEAR_SELECTION to CarPropertyManager.SENSOR_RATE_ONCHANGE,
                VehiclePropertyIds.HVAC_FAN_SPEED to CarPropertyManager.SENSOR_RATE_ONCHANGE,
                VehiclePropertyIds.HVAC_POWER_ON to CarPropertyManager.SENSOR_RATE_ONCHANGE,
                VehiclePropertyIds.HVAC_AC_ON to CarPropertyManager.SENSOR_RATE_ONCHANGE,
                VehiclePropertyIds.HVAC_AUTO_ON to CarPropertyManager.SENSOR_RATE_ONCHANGE,
                VehiclePropertyIds.HVAC_TEMPERATURE_SET to CarPropertyManager.SENSOR_RATE_ONCHANGE,
                VehiclePropertyIds.PERF_VEHICLE_SPEED to CarPropertyManager.SENSOR_RATE_UI,
                VehiclePropertyIds.EV_BATTERY_LEVEL to CarPropertyManager.SENSOR_RATE_UI,
                VehiclePropertyIds.FUEL_LEVEL to CarPropertyManager.SENSOR_RATE_UI,
            ).forEach { (propertyId, rate) ->
                try {
                    manager.registerCallback(callback, propertyId, rate)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to register property $propertyId with rate $rate", e)
                }
            }

            val initialFanSpeed = try {
                manager.getIntProperty(
                    VehiclePropertyIds.HVAC_FAN_SPEED,
                    VehicleAreaSeat.SEAT_ROW_1_LEFT
                )
            } catch (e1: Exception) {
                try {
                    manager.getIntProperty(
                        VehiclePropertyIds.HVAC_FAN_SPEED,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL
                    )
                } catch (e2: Exception) {
                    1
                }
            }

            val initialSpeed = try {
                val rawMps = manager.getFloatProperty(
                    VehiclePropertyIds.PERF_VEHICLE_SPEED,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL
                )
                if (rawMps <= 0f) 0f else rawMps * 3.6f
            } catch (e: Exception) {
                Log.w(TAG, "Initial speed fetch failed", e)
                0f
            }

            val initialFuel = try {
                manager.getFloatProperty(
                    VehiclePropertyIds.EV_BATTERY_LEVEL,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL
                )
            } catch (e: Exception) {
                try {
                    manager.getFloatProperty(
                        VehiclePropertyIds.FUEL_LEVEL,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL
                    )
                } catch (e2: Exception) {
                    Log.w(TAG, "Initial fuel fetch failed", e2)
                    null
                }
            }

            updateState {
                it.copy(
                    isCarConnected = true,
                    fanSpeed = initialFanSpeed,
                    speedKmh = initialSpeed,
                    fuelLevelPercent = initialFuel,
                    errorMessage = null,
                )
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to start VHAL monitoring", exception)
            stopMonitoringOnWorker()
            updateState { it.copy(errorMessage = exception.message ?: "Unable to connect to VHAL") }
        }
    }

    fun increaseFanSpeed() {
        repositoryScope.launch {
            val current = uiState.value.fanSpeed ?: 1
            val max = uiState.value.maxFanSpeed
            if(current == max) return@launch
            val nextSpeed = if (current >= max) 1 else current + 1
            setFanSpeedOnWorker(nextSpeed)
        }
    }

    fun decreaseFanSpeed() {
        repositoryScope.launch {
            val current = uiState.value.fanSpeed ?: 1
            val min = 1
            if(current == min) return@launch
            val nextSpeed = if (current <= min) uiState.value.maxFanSpeed else current - 1
            setFanSpeedOnWorker(nextSpeed)
        }
    }

    fun setMaxFanSpeed() {
        repositoryScope.launch {
            setFanSpeedOnWorker(uiState.value.maxFanSpeed)
        }
    }

    fun toggleHvacPower() {
        repositoryScope.launch {
            val newState = !uiState.value.isHvacPowerOn
            setHvacPowerOnWorker(newState)
        }
    }

    fun toggleAc() {
        repositoryScope.launch {
            val newState = !uiState.value.isAcOn
            setAcOnWorker(newState)
        }
    }

    fun toggleAutoClimate() {
        repositoryScope.launch {
            val newState = !uiState.value.isAutoClimateOn
            setAutoClimateOnWorker(newState)
        }
    }

    fun increaseTemperature() {
        repositoryScope.launch {
            val current = uiState.value.hvacTemperatureC ?: 21.5f
            val next = (current + 0.5f).coerceAtMost(30.0f)
            setTemperatureOnWorker(next)
        }
    }

    fun decreaseTemperature() {
        repositoryScope.launch {
            val current = uiState.value.hvacTemperatureC ?: 21.5f
            val next = (current - 0.5f).coerceAtLeast(16.0f)
            setTemperatureOnWorker(next)
        }
    }

    private fun setFanSpeedOnWorker(fanSpeed: Int) {
        val manager = propertyManager ?: run {
            updateState { it.copy(fanSpeed = fanSpeed, errorMessage = "Car property service is not ready") }
            return
        }
        try {
            try {
                manager.setIntProperty(
                    VehiclePropertyIds.HVAC_FAN_SPEED,
                    VehicleAreaSeat.SEAT_ROW_1_LEFT,
                    fanSpeed
                )
            } catch (e: Exception) {
                manager.setIntProperty(
                    VehiclePropertyIds.HVAC_FAN_SPEED,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    fanSpeed
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "VHAL write for fan speed was blocked or unhandled, updating local UI state", e)
        }
        updateState { it.copy(fanSpeed = fanSpeed, errorMessage = null) }
    }

    private fun setHvacPowerOnWorker(enabled: Boolean) {
        val manager = propertyManager ?: return
        try {
            try {
                manager.setBooleanProperty(
                    VehiclePropertyIds.HVAC_POWER_ON,
                    VehicleAreaSeat.SEAT_ROW_1_LEFT,
                    enabled
                )
            } catch (e: Exception) {
                manager.setBooleanProperty(
                    VehiclePropertyIds.HVAC_POWER_ON,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    enabled
                )
            }
            updateState { it.copy(isHvacPowerOn = enabled, errorMessage = null) }
        } catch (e: Exception) {
            updateState { it.copy(isHvacPowerOn = enabled, errorMessage = null) }
        }
    }

    private fun setAcOnWorker(enabled: Boolean) {
        val manager = propertyManager ?: return
        try {
            try {
                manager.setBooleanProperty(
                    VehiclePropertyIds.HVAC_AC_ON,
                    VehicleAreaSeat.SEAT_ROW_1_LEFT,
                    enabled
                )
            } catch (e: Exception) {
                manager.setBooleanProperty(
                    VehiclePropertyIds.HVAC_AC_ON,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    enabled
                )
            }
            updateState { it.copy(isAcOn = enabled, errorMessage = null) }
        } catch (e: Exception) {
            updateState { it.copy(isAcOn = enabled, errorMessage = null) }
        }
    }

    private fun setAutoClimateOnWorker(enabled: Boolean) {
        val manager = propertyManager ?: return
        try {
            try {
                manager.setBooleanProperty(
                    VehiclePropertyIds.HVAC_AUTO_ON,
                    VehicleAreaSeat.SEAT_ROW_1_LEFT,
                    enabled
                )
            } catch (e: Exception) {
                manager.setBooleanProperty(
                    VehiclePropertyIds.HVAC_AUTO_ON,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    enabled
                )
            }
            updateState { it.copy(isAutoClimateOn = enabled, errorMessage = null) }
        } catch (e: Exception) {
            updateState { it.copy(isAutoClimateOn = enabled, errorMessage = null) }
        }
    }

    private fun setTemperatureOnWorker(temp: Float) {
        val manager = propertyManager ?: run {
            updateState { it.copy(hvacTemperatureC = temp) }
            return
        }
        try {
            try {
                manager.setFloatProperty(
                    VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                    VehicleAreaSeat.SEAT_ROW_1_LEFT,
                    temp
                )
            } catch (e: Exception) {
                manager.setFloatProperty(
                    VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    temp
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "VHAL write for temperature set was blocked or unhandled, updating local UI state", e)
        }
        updateState { it.copy(hvacTemperatureC = temp, errorMessage = null) }
    }

    private fun getVehicleInfo() {
        val info = try {
            val make = propertyManager?.getProperty<String>(
                VehiclePropertyIds.INFO_MAKE,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL
            )?.value ?: "Unknown Manufacturer"

            val model = propertyManager?.getProperty<String>(
                VehiclePropertyIds.INFO_MODEL,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL
            )?.value ?: "Unknown Model"

            val year = propertyManager?.getProperty<Int>(
                VehiclePropertyIds.INFO_MODEL_YEAR,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL
            )?.value ?: 0

            CarInfoData(model, year, make)
        } catch (e: SecurityException) {
            CarInfoData("Permission Denied", 0, "Permission Denied")
        } catch (e: Exception) {
            CarInfoData("Unavailable", 0, "Unavailable")
        }
        updateState { it.copy(carInfo = info) }
    }

    fun stopMonitoring() {
        stopMonitoringOnWorker()
    }

    private fun stopMonitoringOnWorker() {
        propertyManager?.let { manager ->
            listOf(
                VehiclePropertyIds.GEAR_SELECTION,
                VehiclePropertyIds.HVAC_FAN_SPEED,
                VehiclePropertyIds.HVAC_POWER_ON,
                VehiclePropertyIds.HVAC_AC_ON,
                VehiclePropertyIds.HVAC_AUTO_ON,
                VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                VehiclePropertyIds.EV_BATTERY_LEVEL,
                VehiclePropertyIds.FUEL_LEVEL,
            ).forEach { propertyId -> manager.unregisterCallback(callback, propertyId) }
        }
        propertyManager = null
        car?.disconnect()
        car = null
        updateState { it.copy(isCarConnected = false) }
    }

    private fun updateFanSpeed(value: Any?) {
        val fanSpeed = (value as? Int) ?: (value as? Number)?.toInt() ?: return
        updateState { it.copy(fanSpeed = fanSpeed, errorMessage = null) }
    }

    private fun updateHvacPower(value: Any?) {
        val enabled = when (value) {
            is Boolean -> value
            is Int -> value != 0
            else -> return
        }
        updateState { it.copy(isHvacPowerOn = enabled, errorMessage = null) }
    }

    private fun updateAcState(value: Any?) {
        val enabled = when (value) {
            is Boolean -> value
            is Int -> value != 0
            else -> return
        }
        updateState { it.copy(isAcOn = enabled, errorMessage = null) }
    }

    private fun updateAutoClimateState(value: Any?) {
        val enabled = when (value) {
            is Boolean -> value
            is Int -> value != 0
            else -> return
        }
        updateState { it.copy(isAutoClimateOn = enabled, errorMessage = null) }
    }

    private fun updateTemperature(value: Any?) {
        val temp = (value as? Float) ?: (value as? Number)?.toFloat() ?: return
        updateState { it.copy(hvacTemperatureC = temp, errorMessage = null) }
    }

    private fun updateGear(value: Any?) {
        val gear = value as? Int ?: return
        updateState { it.copy(gearSelection = gear, errorMessage = null) }
    }


    private fun updateSpeed(value: Any?) {
        val rawSpeedMps = (value as? Number)?.toFloat() ?: return

        // Ignore invalid floating points
        if (rawSpeedMps.isNaN() || rawSpeedMps.isInfinite()) return

        // Clamp negative, -0.0f, and sub-zero values strictly to 0f
        val speedKmh = if (rawSpeedMps <= 0f) 0f else rawSpeedMps * 3.6f

        updateState { it.copy(speedKmh = speedKmh, errorMessage = null) }
    }

    private fun updateFuel(value: Any?) {
        val fuel = (value as? Float) ?: (value as? Number)?.toFloat() ?: return
        updateState { it.copy(fuelLevelPercent = fuel, errorMessage = null) }
    }

    private inline fun updateState(transform: (VehicleUiState) -> VehicleUiState) {
        mutableUiState.value = transform(mutableUiState.value)
    }

    private companion object {
        const val TAG = "VehiclePropertyRepository"
    }
}
