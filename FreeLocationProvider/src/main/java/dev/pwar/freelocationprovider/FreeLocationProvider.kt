package dev.pwar.freelocationprovider

import android.content.Context
import dev.pwar.freelocationprovider.domain.LocationModel
import dev.pwar.freelocationprovider.domain.SensorDataModel
import dev.pwar.freelocationprovider.framework.InMemoryLocationModelDataSource
import dev.pwar.freelocationprovider.framework.InMemorySensorDataModelDataSource
import dev.pwar.freelocationprovider.usecase.UseCaseEngine
import dev.pwar.freelocationprovider.usecase.UseGpsLinearAccelKalmanEngine
import dev.pwar.freelocationprovider.usecase.UseGpsOnlyEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

class FreeLocationProvider(
    private val engine: UseCaseEngine
) {
    suspend fun feedLocation(location: LocationModel){
        this.engine.feedGpsLocation(location)
    }

    suspend fun feedSensor(sensorData: SensorDataModel) {
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

    /**
     * Builder for [FreeLocationProvider]
     * @param context The activity context
     */
    class Builder(
        private val context: Context
    ){

        enum class EngineType {
            INVALID,            // Invalid engine type
            GPS_EXTRAPOLATION,  // Uses GPS only, with basic extrapolation when signal is lost
            FUSED               // Fused engine with GPS and sensors
        }

        /**
         * Engine type to be used, defaults to [EngineType.FUSED], leave it as such except for
         * debugging puposes.
         */
        var engineType: EngineType = EngineType.FUSED

        /**
         * Delay between each update emitted by the engine in milliseconds
         */
        var sampleTimeLocationUpdateMs: Long = 1000L

        /**
         * The coroutine scope used to launch background jobs, this should typically be
         * either lifecycleScope or viewModelScope so the jobs are bound to the activity lifecycle.
         */
        var coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

        /**
         * The dispatcher to be used to run the background jobs, this should typically be IO
         * to avoid blocking the UI thread.
         */
        var coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO

        /**
         * The maximum tolerated error on a newly received GPS position for it to be applied, any
         * location whose Location.getAccuracy() value is higher than this value will be ignored
         * to avoid the position of the device being affected by inaccurate updates from the GPS.
         * This setting only affects the engine if [EngineType.FUSED] is used.
         * Defaults to 20m.
         */
        var maxLocationAccuracy: Float = 20.0f

        /**
         * Sets parameters for building [FreeLocationProvider]
         */
        fun configure(callback: (Builder) -> Unit): Builder {
            this.apply(callback)
            return this
        }

        /**
         * Builds the [FreeLocationProvider] instance based on [configure] content
         */
        fun build(): FreeLocationProvider {
            when(this.engineType) {
                EngineType.GPS_EXTRAPOLATION -> {
                    val engine = UseGpsOnlyEngine(
                        gpsLocationDataSource = InMemoryLocationModelDataSource(),
                        fusedLocationDataSource = InMemoryLocationModelDataSource(),
                        sensorDataModelDataSource = InMemorySensorDataModelDataSource(),
                        coroutineScope = coroutineScope,
                        coroutineDispatcher = coroutineDispatcher,
                        fusedUpdatesDelayMs = this.sampleTimeLocationUpdateMs
                    )
                    return FreeLocationProvider(engine)
                }
                EngineType.FUSED -> {
                    val engine = UseGpsLinearAccelKalmanEngine(
                        gpsLocationDataSource = InMemoryLocationModelDataSource(),
                        fusedLocationDataSource = InMemoryLocationModelDataSource(),
                        sensorDataModelDataSource = InMemorySensorDataModelDataSource(),
                        coroutineScope = coroutineScope,
                        coroutineDispatcher = coroutineDispatcher,
                        fusedUpdatesDelayMs = this.sampleTimeLocationUpdateMs,
                        maxLocationAccuracy = maxLocationAccuracy
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