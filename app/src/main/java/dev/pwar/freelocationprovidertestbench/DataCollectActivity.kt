package dev.pwar.freelocationprovidertestbench

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import dev.pwar.freelocationprovidertestbench.ui.theme.FreeLocationProviderTestBenchTheme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalDateTime

@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            id = R.id.map
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setCenter(GeoPoint(59.7, 17.8))
            controller.setZoom(18.8)
            Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        }
    }

    // Makes MapView follow the lifecycle of this composable
    val lifecycleObserver = rememberMapLifecycleObserver(mapView)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
        }
    }

    return mapView
}


@Composable
fun rememberMapLifecycleObserver(mapView: MapView): LifecycleEventObserver =
    remember(mapView) {
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
    }

class DataCollectActivity : ComponentActivity(), SensorEventListener {
    private lateinit var posAndroidMarker: Marker
    private lateinit var posGMSMarker: Marker
    private lateinit var logFile: File

    private fun writeToLogFile(msg: String) {
        logFile.appendText("$msg\n")
    }

    private fun logMsg(tag: String, msg: String) {
        Log.d(tag, msg)
        writeToLogFile(msg)
    }

    private fun logSensorData(event: SensorEvent) {
        val now = LocalDateTime.now()
        val msg = "data;$now;${event.sensor.stringType};${event.values.map { "$it" }.joinToString(
            prefix = "", postfix = "", separator = ";"
        )}"

        logMsg("data", msg)
    }

    private fun logPosition(pos: Location, isGoogle: Boolean =false) {
        val now = LocalDateTime.now()
        val tag = if(isGoogle) "googlepos" else "position"
        val msg = "$tag;$now;${pos.latitude};${pos.longitude};${pos.bearing};${pos.accuracy};${pos.speed}"

        logMsg(tag, msg)
    }

    private fun logAccuracy(sensor: Sensor, value: Int){
        val now = LocalDateTime.now()
        val msg = "accuracy;$now;${sensor.name};$value"

        logMsg("accuracy", msg)
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logDirName = "${this.applicationInfo.dataDir}/datalog"
        File(logDirName).apply {
            mkdir()
        }

        val logFileName = "$logDirName/log-start-${Build.MODEL}-${LocalDateTime.now()}.csv"
        logFile = File(logFileName).apply {
            createNewFile()
        }

        val locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val linAccSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val rotVecSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val rotVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        // Listen to sensors
        sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, linAccSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, rotVecSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, rotVector, SensorManager.SENSOR_DELAY_UI)

        // Permission check
        val requestPermissionLauncher = registerForActivityResult(RequestPermission()) {}
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION


                ) == PackageManager.PERMISSION_GRANTED -> {
                    // You can use the API that requires the permission.
                }
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
            }
                else -> {
                    // You can directly ask for the permission.
                    // The registered ActivityResultCallback gets the result of this request.
                    requestPermissionLauncher.launch(
                        Manifest.permission.ACCESS_FINE_LOCATION

                    )
                }
            }

        }

        // Composable
        setContent {
            val mapViewState = rememberMapViewWithLifecycle()
            var androidPos by remember { mutableStateOf(GeoPoint(0.0, 0.0)) }
            var googlePos by remember { mutableStateOf(GeoPoint(0.0, 0.0)) }

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0.0f, object : LocationListener {
                override fun onLocationChanged(p0: Location) {
                    val position = GeoPoint(p0.latitude, p0.longitude)
                    if (!this@DataCollectActivity::posAndroidMarker.isInitialized) {
                        posAndroidMarker = Marker(mapViewState)
                        posAndroidMarker.title = "Android"
                        posAndroidMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        posAndroidMarker.icon = ContextCompat.getDrawable(this@DataCollectActivity, R.drawable.baseline_place_24)
                        mapViewState.overlays.add(posAndroidMarker)
                    }
                    posAndroidMarker.position = position
                    androidPos = position
                    logPosition(p0)
                }
            })

            // Fused
            val locRequest = LocationRequest.Builder(500)
                .build()
            fusedLocationClient.requestLocationUpdates(locRequest, object: LocationCallback() {
                override fun onLocationResult(p0: LocationResult) {
                    val location = p0.lastLocation ?: return
                    val position = GeoPoint(location.latitude ?: 0.0, location.longitude ?: 0.0)
                    mapViewState.controller?.animateTo(position)

                    if(!this@DataCollectActivity::posGMSMarker.isInitialized) {
                        posGMSMarker = Marker(mapViewState).apply {
                            title = "GMS"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            icon = ContextCompat.getDrawable(this@DataCollectActivity, R.drawable.baseline_blue_place_24)
                        }
                        mapViewState.overlays.add(posGMSMarker)
                    }
                    posGMSMarker.position = position
                    googlePos = position
                    logPosition(location, isGoogle = true)
                }
            }, this.mainLooper)

            FreeLocationProviderTestBenchTheme {
                val configuration = LocalConfiguration.current

                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Column(verticalArrangement = Arrangement.Top) {
                        Box(Modifier.size(width = configuration.screenWidthDp.dp, height = configuration.screenHeightDp.dp - 100.dp)) {
                            AndroidView({mapViewState})
                        }
                        Box(Modifier.size(height = 100.dp, width = configuration.screenWidthDp.dp)) {
                            Column() {
                                Text(text = "Android position: ${androidPos}")
                                Text(text = "Google position: ${googlePos}")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSensorChanged(p0: SensorEvent?) {
        if (p0 == null) return
        logSensorData(p0)
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        if (p0 == null) return
        logAccuracy(p0, p1)
    }
}


