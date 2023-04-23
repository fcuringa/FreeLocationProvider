package dev.pwar.freelocationprovidermapbox.interfaces

import android.location.Location
import dev.pwar.freelocationprovider.domain.LocationModel
import java.time.ZoneOffset

fun locationModelToLocationMapper(loc: LocationModel): Location {
    val location = Location("FreeLocationProvider")

    location.apply {
        latitude = loc.latitude
        longitude = loc.longitude
        bearing = loc.bearing.toFloat()
        accuracy = loc.accuracy.toFloat()
        speed = loc.speed.toFloat()
        time = loc.timestamp.toEpochSecond(ZoneOffset.UTC) * 1000
    }

    return location
}