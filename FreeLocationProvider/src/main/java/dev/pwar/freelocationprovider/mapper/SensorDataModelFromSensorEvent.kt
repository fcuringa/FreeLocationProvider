package dev.pwar.freelocationprovider.mapper

import android.hardware.Sensor
import android.hardware.SensorEvent
import dev.pwar.freelocationprovider.domain.SensorDataModel
import dev.pwar.freelocationprovider.domain.SensorType
import java.time.LocalDateTime
import java.time.ZoneOffset

fun sensorDataModelFromSensorEvent(event: SensorEvent): SensorDataModel {
    val type = when (event.sensor.type){
        Sensor.TYPE_GYROSCOPE -> SensorType.GYROSCOPE
        Sensor.TYPE_LINEAR_ACCELERATION -> SensorType.LINEAR_ACCELERATION
        Sensor.TYPE_GAME_ROTATION_VECTOR -> SensorType.GAME_ROTATION_VECTOR
        else -> SensorType.UNKNOWN
    }

    return SensorDataModel(
        timestamp = LocalDateTime.now(),
        type = type,
        x = event.values[0].toDouble(),
        y = event.values[1].toDouble(),
        z = event.values[2].toDouble(),
        extra = if (type == SensorType.GAME_ROTATION_VECTOR){
            event.values[3].toDouble()
        } else {
            0.0
        }
    )
}