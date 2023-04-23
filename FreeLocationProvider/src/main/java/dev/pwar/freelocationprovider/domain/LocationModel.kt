package dev.pwar.freelocationprovider.domain

import kotlinx.serialization.Serializable
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

    fun minus(other: LocationModel): LocationModel{
        return LocationModel(
            timestamp = LocalDateTime.MIN,
            latitude = latitude - other.latitude,
            longitude = longitude - other.longitude,
            bearing = bearing - other.bearing,
            speed = speed - other.speed,
            accuracy = accuracy - other.accuracy
        )
    }

    fun isValid(): Boolean{
        return this != DEFAULT_LOCATION_MODEL
    }
}
