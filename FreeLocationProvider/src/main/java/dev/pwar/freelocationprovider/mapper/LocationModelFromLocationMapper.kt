package dev.pwar.freelocationprovider.mapper

import android.location.Location
import dev.pwar.freelocationprovider.domain.LocationModel
import java.time.LocalDateTime
import java.time.ZoneOffset

fun locationModelFromLocationMapper(location: Location): LocationModel {
    return LocationModel(
        timestamp = LocalDateTime.ofEpochSecond(location.time, 0, ZoneOffset.UTC),
        latitude = location.latitude,
        longitude = location.longitude,
        speed = location.speed.toDouble(),
        bearing = location.bearing.toDouble(),
        accuracy = location.accuracy.toDouble()
    )
}