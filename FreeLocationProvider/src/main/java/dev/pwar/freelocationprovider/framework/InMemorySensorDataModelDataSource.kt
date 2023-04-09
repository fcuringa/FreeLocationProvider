package dev.pwar.freelocationprovider.framework

import dev.pwar.freelocationprovider.data.SensorDataModelDataSource
import dev.pwar.freelocationprovider.domain.SensorDataModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class InMemorySensorDataModelDataSource(
    private var sensorData: SensorDataModel = SensorDataModel.SENSOR_DATA_MODEL_DEFAULT,
    private var sensorDataFlow: MutableSharedFlow<SensorDataModel> = MutableSharedFlow(
        extraBufferCapacity=2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
): SensorDataModelDataSource {
    override suspend fun set(sensorData: SensorDataModel) {
        this.sensorData = sensorData
        this.sensorDataFlow.emit(sensorData)
    }

    override fun get(): SensorDataModel {
        return this.sensorData
    }

    override fun getFlow(): SharedFlow<SensorDataModel> {
        return this.sensorDataFlow.asSharedFlow()
    }
}