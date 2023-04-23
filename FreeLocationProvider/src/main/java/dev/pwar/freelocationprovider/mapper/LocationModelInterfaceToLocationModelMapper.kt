package dev.pwar.freelocationprovider.mapper

import dev.pwar.freelocationprovider.domain.LocationModel
import dev.pwar.freelocationprovider.interfaces.LocationModelInterface
import java.time.LocalDateTime

fun locationModelInterfaceToLocationModelMapper(
    loc: LocationModelInterface,
    timestamp: LocalDateTime = LocalDateTime.now()
): LocationModel {
    return LocationModel(
        timestamp = timestamp,
        latitude = loc.latitude,
        longitude = loc.longitude,
        speed = loc.speed,
        bearing = loc.bearing,
        accuracy = loc.accuracy
    )
}