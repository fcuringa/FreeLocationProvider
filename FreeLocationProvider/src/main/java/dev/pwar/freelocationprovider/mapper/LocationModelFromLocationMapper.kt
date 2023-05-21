package dev.pwar.freelocationprovider.mapper

import android.location.Location
import dev.pwar.freelocationprovider.domain.LocationModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

fun locationModelFromLocationMapper(location: Location): LocationModel {
    return LocationModel(
        timestamp = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(location.time/1000),
            ZoneId.systemDefault()
        ),
        latitude = location.latitude,
        longitude = location.longitude,
        speed = location.speed.toDouble(),
        bearing = location.bearing.toDouble(),
        accuracy = location.accuracy.toDouble()
    )
}