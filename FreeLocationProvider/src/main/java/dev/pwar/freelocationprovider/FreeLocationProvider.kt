package dev.pwar.freelocationprovider

import dev.pwar.freelocationprovider.domain.LocationModel
import dev.pwar.freelocationprovider.domain.SensorDataModel
import dev.pwar.freelocationprovider.domain.SensorType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.*

class FreeLocationProvider {
    private var location: LocationModel = LocationModel.DEFAULT_LOCATION_MODEL
    private var locationFlow: MutableSharedFlow<LocationModel> = MutableSharedFlow(
        extraBufferCapacity=2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var lastSensorEmit = LocalDateTime.now()

    private var lastSensorUpdate = LocalDateTime.now()

    suspend fun feedLocation(location: LocationModel){
        this.location = location
        locationFlow.emit(this.location)
    }

    suspend fun feedSensor(sensorData: SensorDataModel) {
        // Must fulfill 2 requirements: right sensor type and valid location is present in cache
        if (sensorData.type == SensorType.LINEAR_ACCELERATION && location.isValid()){
            // time delta
            val dt = ChronoUnit.MILLIS.between(lastSensorUpdate, sensorData.timestamp)/1000.0f
            lastSensorUpdate = sensorData.timestamp

            // Displacement in meters
            val dx = location.speed * sin(location.bearing * PI/180) * dt
            val dy = location.speed * cos(location.bearing * PI/180) * dt

            // To lon, lat
            val dlon = 180/PI * dx / (cos(location.latitude * PI/180) * 6_371_000)
            val dlat = 180/PI * dy / 6_371_000

            // Apply
            this.location = this.location.copy(latitude = this.location.latitude + dlat)
            this.location = this.location.copy(longitude = this.location.longitude + dlon)

            if(ChronoUnit.MILLIS.between(lastSensorEmit, LocalDateTime.now()) > 20){
                lastSensorEmit = LocalDateTime.now()

                this.locationFlow.emit(this.location)
            }
        }
    }

    fun getLocationFlow(): SharedFlow<LocationModel> {
        return this.locationFlow.asSharedFlow()
    }

    fun getLastKnowLocation(): LocationModel {
        return this.location
    }
}