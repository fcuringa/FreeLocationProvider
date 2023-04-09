package dev.pwar.freelocationprovider.domain

import java.time.LocalDateTime

data class SensorDataModel(
    val timestamp: LocalDateTime,
    val type: SensorType,
    val x: Double,
    val y: Double,
    val z: Double
) {
    companion object {
        val SENSOR_DATA_MODEL_DEFAULT = SensorDataModel(
            timestamp = LocalDateTime.MIN,
            type = SensorType.UNKNOWN,
            x = 0.0, y= 0.0, z= 0.0
        )
    }
}