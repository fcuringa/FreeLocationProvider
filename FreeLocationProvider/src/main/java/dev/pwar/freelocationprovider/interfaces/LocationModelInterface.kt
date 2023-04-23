package dev.pwar.freelocationprovider.interfaces

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LocationModelInterface(
    val latitude: Double,
    val longitude: Double,
    val bearing: Double,
    val speed: Double,
    val accuracy: Double
) {
    companion object {
        fun loadFromString(str: String): LocationModelInterface {
            return Json.decodeFromString(str)
        }
    }

    fun dumpToJsonString(): String {
        return Json.encodeToString(this)
    }
}
