package dev.pwar.freelocationprovider.usecase

import android.util.Log
import dev.pwar.freelocationprovider.data.LocationModelDataSource
import dev.pwar.freelocationprovider.data.SensorDataModelDataSource
import dev.pwar.freelocationprovider.domain.LocationModel
import dev.pwar.freelocationprovider.domain.SensorDataModel
import dev.pwar.freelocationprovider.domain.SensorType
import dev.pwar.freelocationprovider.utils.tickerFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(FlowPreview::class)
class UseGpsOnlyEngine(
    private val gpsLocationDataSource: LocationModelDataSource,
    private val sensorDataModelDataSource: SensorDataModelDataSource,
    private val fusedLocationDataSource: LocationModelDataSource,
    private val coroutineScope: CoroutineScope,
    private val fusedUpdatesDelayMs: Long
): UseCaseEngine {

    init {
        coroutineScope.launch {
            Log.d("UseGpsOnlyEngine", "Starting collection")
            var cachedLocation = LocationModel.DEFAULT_LOCATION_MODEL
            var cachedSensorDataLinAcc = SensorDataModel.SENSOR_DATA_MODEL_DEFAULT
            sensorDataModelDataSource.getFlow().combine(gpsLocationDataSource.getFlow()){
                sensorData, gpsLoc ->
                    Log.d("UseGpsOnlyEngine", "Combine $sensorData, $gpsLoc")
                    if (sensorData.type == SensorType.LINEAR_ACCELERATION) cachedSensorDataLinAcc = sensorData
                    if (gpsLoc != cachedLocation) {
                        cachedLocation = gpsLoc
                        processUpdate(gpsLoc, cachedSensorDataLinAcc)
                    } else {
                        processUpdate(fusedLocationDataSource.get(), cachedSensorDataLinAcc)
                    }
            }.collect()
        }
    }

    private var lastSensorUpdate = LocalDateTime.MIN

    private suspend fun processUpdate(location: LocationModel, sensorData: SensorDataModel){
        if (sensorData.type == SensorType.LINEAR_ACCELERATION && location.isValid()){
            if (lastSensorUpdate == LocalDateTime.MIN) lastSensorUpdate = sensorData.timestamp

            Log.d("UseGpsOnlyEngine", "Processing $location, $sensorData")
            // time delta
            val dt = ChronoUnit.MILLIS.between(lastSensorUpdate, sensorData.timestamp)/1000.0f
            lastSensorUpdate = sensorData.timestamp

            // Displacement in meters
            val dx = location.speed * sin(location.bearing * PI/180) * dt
            val dy = location.speed * cos(location.bearing * PI/180) * dt

            // To lon, lat
            val dLon = 180/PI * dx / (cos(location.latitude * PI/180) * 6_371_000)
            val dLat = 180/PI * dy / 6_371_000

            // Apply
            val newLocation = location.copy(
                latitude = location.latitude + dLat,
                longitude = location.longitude + dLon,
                timestamp = sensorData.timestamp
            )

            Log.d("UseGpsOnlyEngine", "Computed dLon=$dLon, dLat=$dLat -> new location $newLocation")

            this.fusedLocationDataSource.set(newLocation)
        }
    }

    override suspend fun feedGpsLocation(location: LocationModel) {
        gpsLocationDataSource.set(location)
    }

    override suspend fun feedSensorData(sensorData: SensorDataModel) {
        sensorDataModelDataSource.set(sensorData)
    }

    override fun getFusedLocationFlow(): SharedFlow<LocationModel> {
        return this.fusedLocationDataSource.getFlow().sample(this.fusedUpdatesDelayMs).shareIn(
            CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly
        )
    }

    override fun getRawLocationFlow(): SharedFlow<LocationModel> {
        return this.gpsLocationDataSource.getFlow()
    }

    override fun getLastKnownLocation(): LocationModel {
        return this.fusedLocationDataSource.get()
    }

}