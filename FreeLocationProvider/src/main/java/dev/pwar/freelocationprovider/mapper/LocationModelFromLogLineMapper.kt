package dev.pwar.freelocationprovider.mapper

import dev.pwar.freelocationprovider.domain.LocationModel
import java.time.LocalDateTime

fun locationModelFromLogLineMapper(line: String): LocationModel{
    if (line.contains("position")){
        val elements = line.split(";")
        val timestamp =
            elements[1].let { timeString -> LocalDateTime.parse(timeString) }
        val lat = elements[2].toDouble()
        val lon = elements[3].toDouble()
        val bearing = elements[4].toDouble()
        val accuracy = elements[5].toDouble()
        val speed = elements[6].toDouble()

        return LocationModel(
            timestamp, lat, lon, bearing, speed, accuracy
        )
    } else {
        throw Error("Invalid log line, must be a position log line")
    }
}