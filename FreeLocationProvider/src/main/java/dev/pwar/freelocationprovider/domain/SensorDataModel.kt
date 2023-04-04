package dev.pwar.freelocationprovider.domain

import java.time.LocalDateTime

data class SensorDataModel(
    val timestamp: LocalDateTime,
    val type: SensorType,
    val x: Double,
    val y: Double,
    val z: Double
)