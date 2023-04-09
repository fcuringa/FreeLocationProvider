package dev.pwar.freelocationprovider.framework

import dev.pwar.freelocationprovider.data.LocationModelDataSource
import dev.pwar.freelocationprovider.domain.LocationModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class InMemoryLocationModelDataSource(
    private var location: LocationModel = LocationModel.DEFAULT_LOCATION_MODEL,
    private var locationFlow: MutableSharedFlow<LocationModel> = MutableSharedFlow(
        extraBufferCapacity=2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
): LocationModelDataSource {
    override suspend fun set(location: LocationModel) {
        this.location = location
        this.locationFlow.emit(location)
    }

    override fun get(): LocationModel {
        return this.location
    }

    override fun getFlow(): SharedFlow<LocationModel> {
        return this.locationFlow.asSharedFlow()
    }
}