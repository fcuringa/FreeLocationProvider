package dev.pwar.freelocationprovider

import dev.pwar.freelocationprovider.domain.LocationModel
import dev.pwar.freelocationprovider.domain.SensorDataModel
import dev.pwar.freelocationprovider.framework.InMemoryLocationModelDataSource
import dev.pwar.freelocationprovider.framework.InMemorySensorDataModelDataSource
import dev.pwar.freelocationprovider.usecase.UseCaseEngine
import dev.pwar.freelocationprovider.usecase.UseGpsOnlyEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlin.reflect.KClass

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

    companion object Builder {
        enum class EngineType {
            INVALID,
            GPS_INTERPOLATION
        }
        class Builder() {
            private var engineType: EngineType = EngineType.GPS_INTERPOLATION
            private var sampleTimeLocationUpdateMs: Long = 20

            fun engineType(type: EngineType): Builder {
                this.engineType = type
                return this
            }

            fun sampleTimeLocationUpdate(sampleTimeMs: Long): Builder{
                this.sampleTimeLocationUpdateMs = sampleTimeMs
                return this
            }

            fun build(): FreeLocationProvider {
                when(this.engineType) {
                    EngineType.GPS_INTERPOLATION -> {
                        val engine = UseGpsOnlyEngine(
                            gpsLocationDataSource = InMemoryLocationModelDataSource(),
                            fusedLocationDataSource = InMemoryLocationModelDataSource(),
                            sensorDataModelDataSource = InMemorySensorDataModelDataSource(),
                            coroutineScope = CoroutineScope(Dispatchers.IO),
                            fusedUpdatesDelayMs = this.sampleTimeLocationUpdateMs
                        )
                        return FreeLocationProvider(engine)
                    }
                    EngineType.INVALID -> {
                        throw Error("Invalid FreeLocationProvider engine")
                    }
                }
            }
        }
    }
}