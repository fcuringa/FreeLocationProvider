package dev.pwar.freelocationprovider.usecase

import dev.pwar.freelocationprovider.domain.LocationModel
import dev.pwar.freelocationprovider.domain.SensorDataModel
import kotlinx.coroutines.flow.SharedFlow

interface UseCaseEngine {
    suspend fun feedGpsLocation(location: LocationModel)
    suspend fun feedSensorData(sensorData: SensorDataModel)
    fun getFusedLocationFlow(): SharedFlow<LocationModel>
    fun getRawLocationFlow(): SharedFlow<LocationModel>
    fun getLastKnownLocation(): LocationModel
}