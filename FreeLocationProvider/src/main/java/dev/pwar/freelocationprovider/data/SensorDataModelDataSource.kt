package dev.pwar.freelocationprovider.data

import dev.pwar.freelocationprovider.domain.SensorDataModel
import kotlinx.coroutines.flow.SharedFlow

interface SensorDataModelDataSource {
    suspend fun set(sensorData: SensorDataModel)
    fun get(): SensorDataModel
    fun getFlow(): SharedFlow<SensorDataModel>
}