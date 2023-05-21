package dev.pwar.freelocationprovidertestbench

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.MutableLiveData
import dev.pwar.freelocationprovider.domain.SensorType
import dev.pwar.freelocationprovidertestbench.ui.theme.FreeLocationProviderTestBenchTheme
import kotlin.math.PI

class SensorReadingActivity: ComponentActivity(), SensorEventListener {
    private val liveGameRotationSensorValues: MutableLiveData<List<Float>> = MutableLiveData(
        emptyList()
    )

    private val rotMatrixLive: MutableLiveData<List<Float>> = MutableLiveData(emptyList())
    private val orientationLive: MutableLiveData<List<Float>> = MutableLiveData(emptyList())

    override fun onSensorChanged(p0: SensorEvent?) {
        Log.d("onSensorChanged", "${p0?.values}")
        when (p0?.sensor?.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                liveGameRotationSensorValues.postValue(p0.values.map { it })
                val rotMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotMatrix, p0.values)
                rotMatrixLive.postValue(rotMatrix.map { it })
                val orientationAngles = FloatArray(3)
                SensorManager.getOrientation(rotMatrix, orientationAngles)
                orientationLive.postValue(orientationAngles.map { it })
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        // Do nothing
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val rotVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        sensorManager.registerListener(this, rotVector, SensorManager.SENSOR_DELAY_UI)

        setContent {
            val gameRotationSensorState = liveGameRotationSensorValues.observeAsState()
            val rotMatrixState = rotMatrixLive.observeAsState()
            val orientationAnglesState = orientationLive.observeAsState()

            FreeLocationProviderTestBenchTheme {

                Surface(modifier = Modifier.fillMaxSize()) {
                    Column() {
                        Text("Game rotation sensor: ${gameRotationSensorState.value}")
                        if(rotMatrixState.value != null && rotMatrixState.value?.size!! > 1){
                            rotMatrixState.value!!.let {
                                Text("R:\t${it.slice(0..2)}")
                                Text("  \t${it.slice(3..5)}")
                                Text("  \t${it.slice(6..8)}")
                            }
                        }
                        Text("Orientation angles: ${orientationAnglesState.value?.map { "$it\n" }}")
                        Text("Orientation angles (deg)\n ${orientationAnglesState.value?.map { "${it*180/ PI}\n" }}")
                    }
                }

            }
        }
    }
}