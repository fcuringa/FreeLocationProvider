package dev.pwar.freelocationprovidertestbench

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.mapbox.geojson.Point
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.replay.history.ReplayEventLocation
import com.mapbox.navigation.core.replay.history.ReplayEventUpdateLocation
import com.mapbox.navigation.dropin.map.MapViewObserver
import dev.pwar.freelocationprovider.FreeLocationProvider
import dev.pwar.freelocationprovider.mapper.locationModelFromLogLineMapper
import dev.pwar.freelocationprovider.mapper.sensorDataModelFromLogLine
import dev.pwar.freelocationprovidertestbench.databinding.MapboxActivityNavigationViewBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

class NavigationReplayFromFileActivity : AppCompatActivity() {
    private lateinit var binding: MapboxActivityNavigationViewBinding
    private lateinit var pointAnnotationManager: PointAnnotationManager

    private val freeLocationProvider: FreeLocationProvider = FreeLocationProvider.Builder(this)
        .build()
    private var curTime = LocalDateTime.MIN

    suspend fun waitUntil(time: LocalDateTime) {
        if (curTime == LocalDateTime.MIN) {
            curTime = time
        }

        println("Currently $curTime, waiting for ${time}...")
        while (curTime < time) {
            delay(25)
            curTime = curTime.plusNanos(25_000_000)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun handleFile(file: File) {
        GlobalScope.launch(Dispatchers.IO) {


            val reader = file.bufferedReader()
            for (line in reader.lineSequence()) {
                // Try to feed sensor data if line is such
                try {
                    val sensorDataModel = sensorDataModelFromLogLine(line)
                    waitUntil(sensorDataModel.timestamp)

                    freeLocationProvider.feedSensor(sensorDataModel)
                    Log.d("handleFile", "Fed $sensorDataModel")
                } catch (_: Error) {
                }

                // Try to feed position data if line is such
                try {
                    val locationModel = locationModelFromLogLineMapper(line)

                    waitUntil(locationModel.timestamp)

                    // Time to apply
                    freeLocationProvider.feedLocation(locationModel)
                    Log.d("handleFile", "Fed $locationModel")
                } catch (_: Error) {
                }
            }

            reader.close()
        }
    }

    private fun bitmapFromDrawableRes(context: Context, @DrawableRes resourceId: Int) =
        convertDrawableToBitmap(AppCompatResources.getDrawable(context, resourceId))

    private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
        if (sourceDrawable == null) {
            return null
        }
        return if (sourceDrawable is BitmapDrawable) {
            sourceDrawable.bitmap
        } else {
            val constantState = sourceDrawable.constantState ?: return null
            val drawable = constantState.newDrawable().mutate()
            val bitmap: Bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth, drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }

    @OptIn(ExperimentalPreviewMapboxNavigationAPI::class, DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MapboxActivityNavigationViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bundle = intent.extras

        val fileName = bundle?.getString("fileName")
        val file = File("${this.applicationInfo.dataDir}/datalog/$fileName")

        // This allows to simulate your location
        binding.navigationView.api.routeReplayEnabled(true)

        val mapView = com.mapbox.maps.MapView(binding.navigationView.context)

        binding.navigationView.registerMapObserver(object : MapViewObserver() {
            override fun onAttached(mapView: com.mapbox.maps.MapView) {
                super.onAttached(mapView)

                val annotationApi = mapView.annotations
                pointAnnotationManager = annotationApi.createPointAnnotationManager()
            }
        })

// Set options for the resulting symbol layer.

        GlobalScope.launch(Dispatchers.Main) {
            freeLocationProvider.getFusedLocationFlow().combine(
                freeLocationProvider.getRawLocationFlow()) {
                fusedLocation, rawLocation -> Pair(fusedLocation, rawLocation)
            }.collect {
                val fusedLocation = it.first
                val rawLocation = it.second
                val pointAnnotationOptionsFused: PointAnnotationOptions = PointAnnotationOptions()
                    // Define a geographic coordinate.
                    .withPoint(Point.fromLngLat(fusedLocation.longitude, fusedLocation.latitude))
                    // Specify the bitmap you assigned to the point annotation
                    // The bitmap will be added to map style automatically.
                    .withIconImage(bitmapFromDrawableRes(this@NavigationReplayFromFileActivity, R.drawable.baseline_blue_place_24)!!)

                val pointAnnotationOptionsRaw: PointAnnotationOptions = PointAnnotationOptions()
                    // Define a geographic coordinate.
                    .withPoint(Point.fromLngLat(rawLocation.longitude, rawLocation.latitude))
                    // Specify the bitmap you assigned to the point annotation
                    // The bitmap will be added to map style automatically.
                    .withIconImage(bitmapFromDrawableRes(this@NavigationReplayFromFileActivity, R.drawable.baseline_place_24)!!)

                if (this@NavigationReplayFromFileActivity::pointAnnotationManager.isInitialized){
                    pointAnnotationManager.deleteAll()
                    pointAnnotationManager.create(pointAnnotationOptionsFused)
                    pointAnnotationManager.create(pointAnnotationOptionsRaw)
                } else {
                    Log.w("pointAnnotationManager", "pointAnnotationManager is not initialized")
                }
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            freeLocationProvider.getFusedLocationFlow().collect {
                Log.d("freeLocationProvider.getLocationFlow", "Applying $it")

                MapboxNavigationApp.current()?.mapboxReplayer?.pushEvents(
                    listOf(
                        ReplayEventUpdateLocation(
                            it.timestamp.toEpochSecond(ZoneOffset.UTC).toDouble(),
                            ReplayEventLocation(
                                lon = it.longitude,
                                lat = it.latitude,
                                provider = null,
                                accuracyHorizontal = it.accuracy,
                                altitude = null,
                                bearing = it.bearing,
                                speed = it.speed,
                                time = it.timestamp.toEpochSecond(ZoneOffset.UTC).toDouble()
                            )
                        )
                    )
                )
            }
        }

        if (fileName != null) {
            Toast.makeText(this.applicationContext, "Using file: $fileName", Toast.LENGTH_LONG)
                .show()
            handleFile(file = file)
        } else {

            Toast.makeText(this.applicationContext, "No file provided", Toast.LENGTH_LONG).show()
        }

    }
}