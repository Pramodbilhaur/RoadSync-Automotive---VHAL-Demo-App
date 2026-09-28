package com.example.vhaldemoapp.ui

import androidx.lifecycle.ViewModel
import com.example.vhaldemoapp.data.VehiclePropertyRepository
import com.example.vhaldemoapp.model.VehicleUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class VhalViewModel @Inject constructor(
    private val repository: VehiclePropertyRepository,
) : ViewModel() {
    val uiState: StateFlow<VehicleUiState> = repository.uiState

    fun increaseFanSpeed() {
        repository.increaseFanSpeed()
    }

    fun decreaseFanSpeed() {
        repository.decreaseFanSpeed()
    }

    fun setMaxFanSpeed() {
        repository.setMaxFanSpeed()
    }

    fun toggleHvacPower() {
        repository.toggleHvacPower()
    }

    fun toggleAc() {
        repository.toggleAc()
    }

    fun toggleAutoClimate() {
        repository.toggleAutoClimate()
    }

    fun increaseTemperature() {
        repository.increaseTemperature()
    }

    fun decreaseTemperature() {
        repository.decreaseTemperature()
    }
}
