package dev.pwar.freelocationprovider.domain

import java.time.LocalDateTime

data class LocationModel(
    val timestamp: LocalDateTime,
    val latitude: Double,
    val longitude: Double,
    val bearing: Double,
    val speed: Double,
    val accuracy: Double
) {
    companion object {
        val DEFAULT_LOCATION_MODEL = LocationModel(
            latitude = 0.0,
            longitude = 0.0,
            bearing = 0.0,
            speed = 0.0,
            accuracy = 0.0,
            timestamp = LocalDateTime.MIN
        )
    }

    fun isValid(): Boolean{
        return this != DEFAULT_LOCATION_MODEL
    }
}
