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
    private val isDebugEnabled: Boolean = true,
    private val logFile: File = File("${context.filesDir}/.FreeLocationProvider/log-start-${Build.MODEL}-${LocalDateTime.now()}.csv"),
    private val locationCacheFile: File = File("${context.filesDir}/.FreeLocationProvider/cache.json")
): LocationEngine, SensorEventListener {

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
        jobByCallback[callback] = coroutineScope.launch {
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
        if (p0 != null){
            coroutineScope.launch {
                provider.feedSensor(
                    sensorDataModelFromSensorEvent(p0)
                )
            }
            if(isDebugEnabled) logSensorData(p0)
        }
    }

    private fun writeToLogFile(msg: String) {
        logFile.appendText("$msg\n")
    }

    private fun logMsg(tag: String, msg: String) {
        Log.d(tag, msg)
        writeToLogFile(msg)
    }

    private fun logSensorData(event: SensorEvent) {
        val now = LocalDateTime.now()
        val msg = "data;$now;${event.sensor.stringType};${event.values.map { "$it" }.joinToString(
            prefix = "", postfix = "", separator = ";"
        )}"

        logMsg("data", msg)
    }

    private fun logPosition(pos: Location) {
        val now = LocalDateTime.now()
        val msg = "position;$now;${pos.latitude};${pos.longitude};${pos.bearing};${pos.accuracy};${pos.speed}"

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
    fun initialize(replayFile: File? = null){
        Log.d(LOG_TAG, "Initializing the engine")
        if (replayFile == null){
            if (isDebugEnabled) {
                logFile.parentFile?.mkdir()
                logFile.createNewFile()
                logFile.parentFile?.listFiles()?.forEach {
                    Log.d(LOG_TAG, "Checking if ${it.name} should be deleted...")
                    val timeDiffSeconds = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - it.lastModified()/1000.0f
                    if(timeDiffSeconds > 14 * 24 * 3600) {
                        it.delete()
                        Log.d(LOG_TAG, "Deleted $it, was created $timeDiffSeconds seconds ago")
                    } else {
                        Log.d(LOG_TAG, "Kept $it, was created $timeDiffSeconds seconds ago")
                    }
                }
            }

            // Not replay mode, use actual sensors
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            val linAccSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            val rotVecSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            val rotVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

            // Listen to sensors
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, linAccSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, rotVecSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, rotVector, SensorManager.SENSOR_DELAY_UI)

            if (ActivityCompat.checkSelfPermission(
                    context,
                    ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                throw Exception("Missing permission, make sure ACCESS_FINE_LOCATION has been granted" +
                        "before initializing this class")
            }

            // Load from cache if possible
            try {
                locationCacheFile.readText().apply {
                    val cachedLocation = LocationModelInterface.loadFromString(this)
                    coroutineScope.launch(Dispatchers.IO) {
                        provider.feedLocation(locationModelInterfaceToLocationModelMapper(cachedLocation))
                    }
                    Log.d(LOG_TAG, "Loaded location from cache: $cachedLocation")
                }
            } catch (err: Exception) {
                Log.d(LOG_TAG, "Could not load location from cache: $err")
            }

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0.0f) {
                Log.d(LOG_TAG, "Update from GPS provider: $it")
                if (isDebugEnabled) logPosition(it)
                if (!gpsUpdateAllowed){
                    Log.d(LOG_TAG, "GPS update was rejected due to not being allowed by user")
                    return@requestLocationUpdates
                }
                coroutineScope.launch(Dispatchers.IO) {
                    Log.d(LOG_TAG, "GPS update allowed, feeding provider")
                    provider.feedLocation(locationModelFromLocationMapper(it))
                }
            }

            // Keep cache updated
            coroutineScope.launch {
                provider.getFusedLocationFlow()
                    .sample(5000) // Avoid writing too often
                    .flowOn(Dispatchers.IO)
                    .collect {
                        try {
                            if (!locationCacheFile.exists()){
                                locationCacheFile.parentFile?.mkdir()
                                locationCacheFile.createNewFile()
                            }
                            locationCacheFile.writeText(
                                locationModelToLocationModelInterfaceMapper(it).dumpToJsonString()
                            )
                            Log.d(LOG_TAG, "Location cache was updated")
                        } catch (err: Exception){
                            Log.d(LOG_TAG, "Could not create location cache: $err")
                        }
                    }
            }
        } else {
            // Replay mode
            if (!replayFile.exists()) throw Exception("Replay file does not exist")
            coroutineScope.launch {
                val reader = replayFile.bufferedReader()
                var hasReceiverFirstPosition = false
                for (line in reader.lineSequence()) {
                    // Try to feed sensor data if line is such
                    if (hasReceiverFirstPosition){
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
                        if (gpsUpdateAllowed || !hasReceiverFirstPosition){
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

}