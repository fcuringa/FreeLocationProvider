package dev.pwar.freelocationprovider.data

import dev.pwar.freelocationprovider.domain.LocationModel
import kotlinx.coroutines.flow.SharedFlow

interface LocationModelDataSource {
    suspend fun set(location: LocationModel)
    fun get(): LocationModel
    fun getFlow(): SharedFlow<LocationModel>
}