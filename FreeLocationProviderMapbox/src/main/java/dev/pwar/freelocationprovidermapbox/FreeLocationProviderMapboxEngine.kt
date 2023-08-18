package dev.pwar.freelocationprovidermapbox

import android.Manifest
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult
import dev.pwar.freelocationprovider.FreeLocationProvider
import dev.pwar.freelocationprovider.interfaces.LocationModelInterface
import dev.pwar.freelocationprovider.mapper.locationModelFromLocationMapper
import dev.pwar.freelocationprovider.mapper.locationModelFromLogLineMapper
import dev.pwar.freelocationprovider.mapper.locationModelInterfaceToLocationModelMapper
import dev.pwar.freelocationprovider.mapper.locationModelToLocationModelInterfaceMapper
import dev.pwar.freelocationprovider.mapper.sensorDataModelFromLogLine
import dev.pwar.freelocationprovider.mapper.sensorDataModelFromSensorEvent
import dev.pwar.freelocationprovidermapbox.interfaces.locationModelToLocationMapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.sample
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

class FreeLocationProviderMapboxEngine(
    private val context: Context,
    private val provider: FreeLocationProvider,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sensorDelay: Int = SensorManager.SENSOR_DELAY_UI,
    private val isDebugEnabled: Boolean = true,
    private val isDebugLogFileEnabled: Boolean = true,
    private val logFile: File = File("${context.filesDir}/.FreeLocationProvider/log-start-${Build.MODEL}-${LocalDateTime.now()}.csv"),
    private val locationCacheFile: File = File("${context.filesDir}/.FreeLocationProvider/cache.json")
) : LocationEngine, SensorEventListener, LocationListener {

    companion object {
        const val LOG_TAG = "FreeLocationProviderMapboxEngine"
    }

    var gpsUpdateAllowed = true
    private var curTime = LocalDateTime.MIN

    private val jobByCallback:
            MutableMap<LocationEngineCallback<LocationEngineResult>, Job> = mutableMapOf()

    override fun getLastLocation(callback: LocationEngineCallback<LocationEngineResult>) {
        val loc = provider.getLastKnownLocation()
        val result = LocationEngineResult.create(locationModelToLocationMapper(loc))
        Log.d(LOG_TAG, "getLastLocation -> $result")
        callback.onSuccess(result)
    }

    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        callback: LocationEngineCallback<LocationEngineResult>,
        looper: Looper?
    ) {
        Log.d(LOG_TAG, "requestLocationUpdates $request, $callback, $looper")
        jobByCallback[callback] = coroutineScope.launch(coroutineDispatcher) {
            provider.getFusedLocationFlow().collect {
                Log.d(LOG_TAG, "requestLocationUpdates.collect -> $it")
                callback.onSuccess(
                    LocationEngineResult.create(locationModelToLocationMapper(it.copy(timestamp = LocalDateTime.now())))
                )
            }
        }
    }

    override fun requestLocationUpdates(
        request: LocationEngineRequest,
        pendingIntent: PendingIntent?
    ) {
        TODO("Not yet implemented")
    }

    override fun removeLocationUpdates(callback: LocationEngineCallback<LocationEngineResult>) {
        jobByCallback[callback]?.cancel()
        jobByCallback.remove(callback)
    }

    override fun removeLocationUpdates(pendingIntent: PendingIntent?) {
        TODO("Not yet implemented")
    }

    override fun onSensorChanged(p0: SensorEvent?) {
        if (!this.coroutineScope.isActive) {
            try {
                val locationManager =
                    context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val sensorManager =
                    context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

                sensorManager.unregisterListener(this)
                locationManager.removeUpdates(this)
                Log.d(LOG_TAG, "Unregistered sensor and location listeners")
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Could not unregister listeners ${e.stackTraceToString()}")
            }
            return
        }
        if (p0 != null) {
            coroutineScope.launch(coroutineDispatcher) {
                provider.feedSensor(
                    sensorDataModelFromSensorEvent(p0)
                )
            }
            if (isDebugEnabled) logSensorData(p0)
        }
    }

    private fun writeToLogFile(msg: String) {
        logFile.appendText("$msg\n")
    }

    private fun logMsg(tag: String, msg: String) {
        Log.d(tag, msg)
        if (isDebugLogFileEnabled) writeToLogFile(msg)
    }

    private fun logSensorData(event: SensorEvent) {
        val now = LocalDateTime.now()
        val msg = "data;$now;${event.sensor.stringType};${
            event.values.map { "$it" }.joinToString(
                prefix = "", postfix = "", separator = ";"
            )
        }"

        logMsg("data", msg)
    }

    private fun logPosition(pos: Location) {
        val now = LocalDateTime.now()
        val msg =
            "position;$now;${pos.latitude};${pos.longitude};${pos.bearing};${pos.accuracy};${pos.speed}"

        logMsg("position", msg)
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // TODO pass this data to the provider
    }

    /**
     * Initializes the engine, this is required for the tracking to start. It can only be done once.
     * By default, it will use the GPS and sensors to track the location. Alternatively, a replay
     * file can be passed.
     *
     * @param replayFile File to read the replay data from. Leave null to not use replay mode
     */
    fun initialize(replayFile: File? = null) {
        Log.d(LOG_TAG, "Initializing the engine")
        if (replayFile == null) {
            if (isDebugEnabled) {
                logFile.parentFile?.mkdir()
                logFile.createNewFile()
                logFile.parentFile?.listFiles()?.forEach {
                    Log.d(LOG_TAG, "Checking if ${it.name} should be deleted...")
                    val timeDiffSeconds = LocalDateTime.now()
                        .toEpochSecond(ZoneOffset.UTC) - it.lastModified() / 1000.0f
                    if (timeDiffSeconds > 14 * 24 * 3600) {
                        it.delete()
                        Log.d(LOG_TAG, "Deleted $it, was created $timeDiffSeconds seconds ago")
                    } else {
                        Log.d(LOG_TAG, "Kept $it, was created $timeDiffSeconds seconds ago")
                    }
                }
            }

            // Not replay mode, use actual sensors
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            val linAccSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            val rotVecSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            val rotVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

            // Listen to sensors
            sensorManager.registerListener(this, gravitySensor, sensorDelay)
            sensorManager.registerListener(this, linAccSensor, sensorDelay)
            sensorManager.registerListener(this, rotVecSensor, sensorDelay)
            sensorManager.registerListener(this, accelSensor, sensorDelay)
            sensorManager.registerListener(this, gyroSensor, sensorDelay)
            sensorManager.registerListener(this, rotVector, sensorDelay)

            if (ActivityCompat.checkSelfPermission(
                    context,
                    ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                throw Exception(
                    "Missing permission, make sure ACCESS_FINE_LOCATION has been granted" +
                            "before initializing this class"
                )
            }

            // Load from cache if possible
            try {
                locationCacheFile.readText().apply {
                    val cachedLocation = LocationModelInterface.loadFromString(this)
                    coroutineScope.launch(coroutineDispatcher) {
                        provider.feedLocation(
                            locationModelInterfaceToLocationModelMapper(
                                cachedLocation
                            )
                        )
                    }
                    Log.d(LOG_TAG, "Loaded location from cache: $cachedLocation")
                }
            } catch (err: Exception) {
                Log.d(LOG_TAG, "Could not load location from cache: $err")
            }

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0.0f, this)

            // Keep cache updated
            coroutineScope.launch(coroutineDispatcher) {
                provider.getFusedLocationFlow()
                    .sample(5000) // Avoid writing too often
                    .flowOn(Dispatchers.IO)
                    .collect {
                        try {
                            if (!locationCacheFile.exists()) {
                                locationCacheFile.parentFile?.mkdir()
                                locationCacheFile.createNewFile()
                            }
                            locationCacheFile.writeText(
                                locationModelToLocationModelInterfaceMapper(it).dumpToJsonString()
                            )
                            Log.d(LOG_TAG, "Location cache was updated")
                        } catch (err: Exception) {
                            Log.d(LOG_TAG, "Could not create location cache: $err")
                        }
                    }
            }
        } else {
            // Replay mode
            if (!replayFile.exists()) throw Exception("Replay file does not exist")
            coroutineScope.launch(coroutineDispatcher) {
                val reader = replayFile.bufferedReader()
                var hasReceiverFirstPosition = false
                for (line in reader.lineSequence()) {
                    // Try to feed sensor data if line is such
                    if (hasReceiverFirstPosition) {
                        try {
                            val sensorDataModel = sensorDataModelFromLogLine(line)
                            waitUntil(sensorDataModel.timestamp)
                            provider.feedSensor(sensorDataModel)
                            Log.d("handleFile", "Fed sensor data $sensorDataModel")
                        } catch (_: Error) {
                        }
                    }


                    // Try to feed position data if line is such
                    try {
                        val locationModel = locationModelFromLogLineMapper(line)

                        waitUntil(locationModel.timestamp)

                        // Time to apply
                        if (gpsUpdateAllowed || !hasReceiverFirstPosition) {
                            provider.feedLocation(locationModel)
                            Log.d("handleFile", "Fed location $locationModel")
                            hasReceiverFirstPosition = true
                        }
                    } catch (_: Error) {
                    }
                }
                reader.close()
            }
        }

    }

    suspend fun waitUntil(time: LocalDateTime) {
        if (curTime == LocalDateTime.MIN) {
            curTime = time
        }

        println("Currently $curTime, waiting for ${time}...")
        while (curTime < time) {
            delay(4)
            curTime = curTime.plusNanos(5_000_000)
        }
    }

    override fun onLocationChanged(p0: Location) {
        Log.d(LOG_TAG, "Update from GPS provider: $p0")
        if (isDebugEnabled) logPosition(p0)
        if (!gpsUpdateAllowed) {
            Log.d(LOG_TAG, "GPS update was rejected due to not being allowed by user")
            return
        }
        coroutineScope.launch(coroutineDispatcher) {
            Log.d(LOG_TAG, "GPS update allowed, feeding provider")
            provider.feedLocation(locationModelFromLocationMapper(p0))
        }
    }

    class Builder(private val context: Context) {

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
         * Enables the logging of sensor and position raw data to logcat
         */
        var isDebugEnabled: Boolean = true

        /**
         * Enables saving the sensor and position raw data to logcat to [logFile]. The parameter
         * [isDebugEnabled] must be true to use this.
         */
        var isDebugLogFileEnabled: Boolean = true

        /**
         * The destination log file in which the sensor and position raw data will be saved if
         * [isDebugLogFileEnabled] is true.
         */
        var logFile: File =
            File("${context.filesDir}/.FreeLocationProvider/log-start-${Build.MODEL}-${LocalDateTime.now()}.csv")

        /**
         * The cache file in which the latest know location will be saved so we can position the
         * device quickly if the application is shutdown.
         */
        var locationCacheFile: File = File("${context.filesDir}/.FreeLocationProvider/cache.json")

        /**
         * The sensor delay value to be used for sensor even updates, should be one of
         * [SensorManager.SENSOR_DELAY_UI], [SensorManager.SENSOR_DELAY_FASTEST],
         * [SensorManager.SENSOR_DELAY_GAME],
         * [SensorManager.SENSOR_DELAY_NORMAL]. Defaults to [SensorManager.SENSOR_DELAY_UI]
         */
        var sensorDelay: Int = SensorManager.SENSOR_DELAY_UI

        /**
         * Sets parameters for building [FreeLocationProviderMapboxEngine]
         */
        fun configure(callback: (Builder) -> Unit): Builder {
            this.apply(callback)
            return this
        }

        /**
         * Build [FreeLocationProviderMapboxEngine] based on [configure] content
         */
        fun build(
            provider: FreeLocationProvider
        ): FreeLocationProviderMapboxEngine {
            return FreeLocationProviderMapboxEngine(
                context = context,
                provider = provider,
                coroutineScope,
                coroutineDispatcher,
                sensorDelay,
                isDebugEnabled,
                isDebugLogFileEnabled,
                logFile,
                locationCacheFile
            )
        }
    }

}