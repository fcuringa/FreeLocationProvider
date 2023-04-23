package dev.pwar.freelocationprovider.usecase

import android.util.Log
import dev.pwar.freelocationprovider.data.LocationModelDataSource
import dev.pwar.freelocationprovider.data.SensorDataModelDataSource
import dev.pwar.freelocationprovider.domain.LocationModel
import dev.pwar.freelocationprovider.domain.SensorDataModel
import dev.pwar.freelocationprovider.domain.SensorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.ejml.simple.SimpleMatrix
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.*

class UseGpsLinearAccelKalmanEngine(
    private val gpsLocationDataSource: LocationModelDataSource,
    private val sensorDataModelDataSource: SensorDataModelDataSource,
    private val fusedLocationDataSource: LocationModelDataSource,
    private val coroutineScope: CoroutineScope,
    private val fusedUpdatesDelayMs: Long
) : UseCaseEngine {
    private var cachedLocation = LocationModel.DEFAULT_LOCATION_MODEL

    init {
        coroutineScope.launch {
            var lastGyroUpdate = LocalDateTime.MIN
            sensorDataModelDataSource.getFlow()
                .filter { it.type == SensorType.GYROSCOPE }
                .collect {
                    val fusedLoc = fusedLocationDataSource.get()
                    if (lastGyroUpdate == LocalDateTime.MIN){
                        lastGyroUpdate = it.timestamp
                    }
                    if (fusedLoc.isValid()){
                        val dt = ChronoUnit.MILLIS.between(lastGyroUpdate, it.timestamp) / 1000.0
                        val newFusedLoc = fusedLoc.copy(
                            bearing = fusedLoc.bearing + dt - it.y * 180/ PI
                        )

                        fusedLocationDataSource.set(newFusedLoc)
                        Log.d("UseGpsLinearAccelKalmanEngine",
                            "Gyro update of fused loc: $newFusedLoc")
                    }

                    lastGyroUpdate = it.timestamp
                }
        }

        coroutineScope.launch {
            Log.d("UseGpsLinearAccelKalmanEngine", "Starting collection")
            var cachedSensorDataLinAcc = SensorDataModel.SENSOR_DATA_MODEL_DEFAULT
            sensorDataModelDataSource.getFlow()
                .filter { it.type == SensorType.LINEAR_ACCELERATION }
                .combine(
                    gpsLocationDataSource.getFlow().filter { it.isValid() }) { sensorData, gpsLoc ->
                    Log.d("UseGpsLinearAccelKalmanEngine", "Combine $sensorData, $gpsLoc")
                    if (!cachedLocation.isValid() && gpsLoc.isValid()) {
                        globalX = locationModelToState(gpsLoc)
                    }
                    if (!gpsLoc.isValid()) return@combine

                    if (sensorData.type == SensorType.LINEAR_ACCELERATION) cachedSensorDataLinAcc =
                        sensorData

                    if (gpsLoc != cachedLocation) {
                        cachedLocation = gpsLoc
                        processUpdate(gpsLoc, cachedSensorDataLinAcc)
                    } else {
                        processUpdate(fusedLocationDataSource.get(), cachedSensorDataLinAcc)
                    }
                }.collect()
        }
    }

    private fun locationModelToState(location: LocationModel): SimpleMatrix {
        val x = PI / 180.0 * location.longitude * cos(location.latitude * PI / 180.0) * 6_371_000
        val y = PI / 180.0 * location.latitude * 6_371_000

        val vy = location.speed * cos(location.bearing * PI / 180.0)
        val vx = location.speed * sin(location.bearing * PI / 180.0)

        return SimpleMatrix(arrayOf(doubleArrayOf(x, y, vx, vy))).transpose()
    }

    private fun stateToLocationModel(matX: SimpleMatrix, timestamp: LocalDateTime): LocationModel {
        val lat = matX[1] * 180 / PI / 6_371_000
        val lon = matX[0] * 180 / PI / (6_371_000 * cos(lat * PI / 180))

        val speed = sqrt(matX[2].pow(2) + matX[3].pow(2))

        val bearing = 90 - atan2(matX[3], matX[2]) * 180 / PI

        return LocationModel(
            latitude = lat,
            longitude = lon,
            bearing = bearing,
            speed = speed,
            timestamp = timestamp,
            accuracy = 8.0
        )
    }

    private var lastSensorUpdate = LocalDateTime.MIN

    private lateinit var globalX: SimpleMatrix

    private var globalMatP = SimpleMatrix.identity(4).scale(0.1)

    private suspend fun processUpdate(location: LocationModel, sensorData: SensorDataModel) {
        if (!this::globalX.isInitialized) {
            Log.d(
                "UseGpsLinearAccelKalmanEngine",
                "Skipping state update as state not initialized yet"
            )
            return
        }
        if (lastSensorUpdate == LocalDateTime.MIN) {
            lastSensorUpdate = sensorData.timestamp
        }

        val dt = ChronoUnit.MILLIS.between(lastSensorUpdate, sensorData.timestamp) / 1000.0

        // A matrix
        val matA = SimpleMatrix(
            arrayOf(
                doubleArrayOf(1.0, 0.0, dt, 0.0),
                doubleArrayOf(0.0, 1.0, 0.0, dt),
                doubleArrayOf(0.0, 0.0, 1.0, 0.0),
                doubleArrayOf(0.0, 0.0, 0.0, 1.0)
            )
        )

        // B matrix
        val matB = SimpleMatrix(
            arrayOf(
                doubleArrayOf(dt.pow(2) / 2.0, 0.0),
                doubleArrayOf(0.0, dt.pow(2) / 2.0),
                doubleArrayOf(dt, 0.0),
                doubleArrayOf(0.0, dt),
            )
        )

        // H matrix
        val matH = if (ChronoUnit.MILLIS.between(cachedLocation.timestamp, sensorData.timestamp) > 1500) {
            SimpleMatrix.diag(0.0, 0.0, 0.0, 0.0)
        } else {
            SimpleMatrix.identity(4)
        }

        // Q matrix, process noise
        val sigmaASquare = 0.1    // Estimate of acceleration std dev (squared)
        val matQ = SimpleMatrix(
            arrayOf(
                doubleArrayOf(
                    dt.pow(4) * sigmaASquare / 4.0,
                    0.0,
                    dt.pow(3) * sigmaASquare / 2.0,
                    0.0
                ),
                doubleArrayOf(
                    0.0,
                    dt.pow(4) * sigmaASquare / 4.0,
                    0.0,
                    dt.pow(3) * sigmaASquare / 2.0
                ),
                doubleArrayOf(dt.pow(3) * sigmaASquare / 2.0, 0.0, dt.pow(2) * sigmaASquare, 0.0),
                doubleArrayOf(0.0, dt.pow(3) * sigmaASquare / 2.0, 0.0, dt.pow(2) * sigmaASquare),
            )
        )

        // R matrix, measurement noise. Set by time since last update
        val sigmaXYSquare = 0.1
        val matR = SimpleMatrix(
            arrayOf(
                doubleArrayOf(sigmaXYSquare, 0.0, 0.0, 0.0),
                doubleArrayOf(0.0, sigmaXYSquare, 0.0, 0.0),
                doubleArrayOf(0.0, 0.0, 0.5, 0.0),
                doubleArrayOf(0.0, 0.0, 0.0, 0.5),
            )
        )

        // Input
        // TODO find correct relation
        val matU = SimpleMatrix(arrayOf(doubleArrayOf(
            -sensorData.z * sin(location.bearing * PI/180),
            -sensorData.z * cos(location.bearing * PI/180)
        ))).transpose()

        Log.d("UseGpsLinearAccelKalmanEngine", "dt: $dt")
        Log.d("UseGpsLinearAccelKalmanEngine", "Input: $matU")
        Log.d("UseGpsLinearAccelKalmanEngine", "State: $globalX")

        /*
         * Filter steps
         */
        // Step 1 - Prediction
        // Predict state
        val matXPrediction = (matA.mult(globalX)).plus((matB.mult(matU)))

        // Predict P
        val matPPrediction = (matA.mult(globalMatP).mult(matA.transpose())).plus(matQ)

        // Step 2 - Measure, correct
        val matZ = locationModelToState(cachedLocation)
        val matY = matZ.minus(matH.mult(matXPrediction))
        val matS = (matH.mult(matPPrediction).mult(matH.transpose())).plus(matR)

        // Update Kalman gain
        val matK = matPPrediction.mult(matH.transpose()).mult(matS.invert())

        // System state, new
        val newMatX = matXPrediction.plus((matK.mult(matY)))

        // Update P
        val newMatP = matPPrediction.minus((matK.mult(matH).mult(matPPrediction)))

        val newLocation = stateToLocationModel(newMatX, sensorData.timestamp)
        val predictedLocation = stateToLocationModel(matXPrediction, sensorData.timestamp)

        // Log
        Log.d("UseGpsLinearAccelKalmanEngine", "GPS location: $cachedLocation")
        Log.d("UseGpsLinearAccelKalmanEngine", "Predicted location: $predictedLocation")
        Log.d("UseGpsLinearAccelKalmanEngine",
            "Diff GPS/Predicted: ${predictedLocation.minus(cachedLocation)}")
        Log.d("UseGpsLinearAccelKalmanEngine", "Actual new location: $newLocation")
        Log.d("UseGpsLinearAccelKalmanEngine",
            "Diff GPS/New: ${newLocation.minus(cachedLocation)}")

        // Done - update globals
        globalX = newMatX
        globalMatP = newMatP
        lastSensorUpdate = sensorData.timestamp

        fusedLocationDataSource.set(newLocation)
    }

    override suspend fun feedGpsLocation(location: LocationModel) {
        gpsLocationDataSource.set(location)
    }

    override suspend fun feedSensorData(sensorData: SensorDataModel) {
        sensorDataModelDataSource.set(sensorData)
    }

    @OptIn(FlowPreview::class)
    override fun getFusedLocationFlow(): SharedFlow<LocationModel> {
        return this.fusedLocationDataSource.getFlow()
            .filter { it != LocationModel.DEFAULT_LOCATION_MODEL }
            .sample(this.fusedUpdatesDelayMs).shareIn(
            CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly
        )
    }

    override fun getRawLocationFlow(): SharedFlow<LocationModel> {
        return gpsLocationDataSource.getFlow()
    }

    override fun getLastKnownLocation(): LocationModel {
        return fusedLocationDataSource.get()
    }
}