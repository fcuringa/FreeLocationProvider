package dev.pwar.freelocationprovider.mapper

import dev.pwar.freelocationprovider.domain.LocationModel
import dev.pwar.freelocationprovider.interfaces.LocationModelInterface

fun locationModelToLocationModelInterfaceMapper(loc: LocationModel): LocationModelInterface {
    return LocationModelInterface(
        latitude = loc.latitude,
        longitude = loc.longitude,
        speed = loc.speed,
        bearing = loc.bearing,
        accuracy = loc.accuracy
    )
}