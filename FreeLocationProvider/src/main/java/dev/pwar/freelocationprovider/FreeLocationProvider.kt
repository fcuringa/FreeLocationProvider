package dev.pwar.freelocationprovider

import dev.pwar.freelocationprovider.domain.LocationModel
import dev.pwar.freelocationprovider.domain.SensorDataModel
import dev.pwar.freelocationprovider.usecase.UseCaseEngine
import kotlinx.coroutines.flow.*

class FreeLocationProvider(
    private val engine: UseCaseEngine
) {


    suspend fun feedSimulatedLocation(location: LocationModel){
        this.engine.feedGpsLocation(location)
    }

    suspend fun feedSimulatedSensor(sensorData: SensorDataModel) {
        this.engine.feedSensorData(sensorData)
    }

    fun getFusedLocationFlow(): SharedFlow<LocationModel> {
        return this.engine.getFusedLocationFlow()
    }

    fun getLastKnownLocation(): LocationModel {
        return this.engine.getLastKnownLocation()
    }

    fun getRawLocationFlow(): SharedFlow<LocationModel> {
        return this.engine.getRawLocationFlow()
    }
}