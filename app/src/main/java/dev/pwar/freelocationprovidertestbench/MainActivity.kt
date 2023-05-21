package dev.pwar.freelocationprovidertestbench

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import dev.pwar.freelocationprovidertestbench.ui.theme.FreeLocationProviderTestBenchTheme
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    false
                ) -> {
                    // Precise location access granted.
                }

                permissions.getOrDefault(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    false
                ) -> {
                    // Only approximate location access granted.
                }

                else -> {
                    // No location access granted.
                }
            }
        }

        locationPermissionRequest.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )


        val logDirName = "${this.applicationInfo.dataDir}/files/.FreeLocationProvider"
        val allFiles = File(logDirName).listFiles()?.asList()?.takeLast(100)
        setContent {
            val context = LocalContext.current
            FreeLocationProviderTestBenchTheme {
                var expandedReplay by remember { mutableStateOf(false) }
                var expandedNavigation by remember {
                    mutableStateOf(false)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "Welcome to the Free Location Provider test bench")
                            Spacer(modifier = Modifier.size(50.dp))
                            Button(onClick = {
                                context.startActivity(
                                    Intent(
                                        context,
                                        DataCollectActivity::class.java
                                    )
                                )
                            }) {
                                Text("Start Collecting data")
                            }
                            Button(onClick = {
                                context.startActivity(
                                    Intent(
                                        context,
                                        SensorReadingActivity::class.java
                                    )
                                )
                            }) {
                                Text("View sensors")
                            }
                            Button(onClick = {
                                expandedReplay = true
                            }) {
                                Text("Start Replaying data from file...")
                            }
                            Button(onClick = { expandedNavigation = true }) {
                                Text(text = "Start navigating...")
                            }
                        }
                        Box(
                            modifier = Modifier.fillMaxHeight(),
                        ) {
                            DropdownMenu(
                                expanded = expandedReplay,
                                onDismissRequest = { expandedReplay = false },
                            ) {
                                allFiles?.forEach { fileName ->
                                    val elems = fileName.name.split("-", "T", ":")
                                    if (elems.size >= 9) {
                                        val sizeKb = fileName.length() / 1024
                                        val isLarge = sizeKb > 1024
                                        val prettyName =
                                            "${elems[4]}-${elems[5]}-${elems[6]} ${elems[7]}:${elems[8]} (${sizeKb}kB)"
                                        DropdownMenuItem(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                val intent =
                                                    Intent(context, TurnByTurnActivity::class.java)
                                                intent.putExtra("fileName", fileName.name)
                                                intent.putExtra("isFused", true)
                                                context.startActivity(intent)
                                            }) {
                                            if (isLarge) {
                                                Text(
                                                    text = prettyName,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            } else {
                                                Text(text = prettyName)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                    ) {

                        DropdownMenu(
                            expanded = expandedNavigation,
                            onDismissRequest = { expandedNavigation = false }) {
                            Button(onClick = {
                                val intent = Intent(context, TurnByTurnActivity::class.java)
                                context.startActivity(intent)
                            }) {
                                Text(text = "Use default settings")
                            }
                        }
                    }

                }
            }

        }
    }
}