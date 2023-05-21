package dev.pwar.freelocationprovider.mapper

import dev.pwar.freelocationprovider.domain.SensorDataModel
import dev.pwar.freelocationprovider.domain.SensorType
import java.time.LocalDateTime

fun sensorDataModelFromLogLine(line: String): SensorDataModel {
    if (line.contains("android.sensor")){
        val elements = line.split(";")
        val timestamp =
            elements[1].let { timeString -> LocalDateTime.parse(timeString) }
        val type = elements[2].let {
            when(it) {
                "android.sensor.gravity" -> SensorType.GRAVITY
                "android.sensor.gyroscope" -> SensorType.GYROSCOPE
                "android.sensor.linear_acceleration" -> SensorType.LINEAR_ACCELERATION
                "android.sensor.accelerometer" -> SensorType.ACCELEROMETER
                "android.sensor.game_rotation_vector" -> SensorType.GAME_ROTATION_VECTOR
                else -> SensorType.UNKNOWN
            }
        }
        val x = elements[3].toDouble()
        val y = elements[4].toDouble()
        val z = elements[5].toDouble()
        var extra = 0.0
        if (type == SensorType.GAME_ROTATION_VECTOR){
            extra = elements[6].toDouble()
        }

        return SensorDataModel(timestamp, type, x, y, z, extra)
    } else {
        throw Error("Invalid log line, must be a sensor data log line")
    }
}