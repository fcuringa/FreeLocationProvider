package dev.pwar.freelocationprovidertestbench

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pwar.freelocationprovidertestbench.ui.theme.FreeLocationProviderTestBenchTheme
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logDirName = "${this.applicationInfo.dataDir}/datalog"
        val allFiles = File(logDirName).listFiles()?.asList()?.takeLast(20)
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
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Welcome to the Free Location Provider test bench")
                        Spacer(modifier = Modifier.size(50.dp))
                        Button(onClick = {
                            context.startActivity(Intent(context, DataCollectActivity::class.java))
                        }) {
                            Text("Start Collecting data")
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
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)) {

                        DropdownMenu(expanded = expandedReplay, onDismissRequest = { expandedReplay = false }) {
                            allFiles?.forEach { fileName ->
                                val elems = fileName.name.split("-", "T", ":")
                                val sizeKb = fileName.length()/1024
                                val isLarge = sizeKb > 1024
                                val prettyName = "${elems[4]}-${elems[5]}-${elems[6]} ${elems[7]}:${elems[8]} (${sizeKb}kB)"
                                DropdownMenuItem(onClick = {
                                    val intent = Intent(context, NavigationReplayFromFileActivity::class.java)
                                    intent.putExtra("fileName", fileName.name)
                                    context.startActivity(intent)
                                }) {
                                    if (isLarge){
                                        Text(text = prettyName, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text(text = prettyName)
                                    }
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)) {

                        DropdownMenu(expanded = expandedNavigation, onDismissRequest = { expandedNavigation = false }) {
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