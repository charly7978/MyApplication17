package com.forensicppg.monitor

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forensicppg.monitor.camera.Camera2PpgController
import com.forensicppg.monitor.ppg.PpgFrameAnalyzer
import com.forensicppg.monitor.ppg.PpgSignalProcessor
import com.forensicppg.monitor.ui.components.PpgWaveRenderer
import com.forensicppg.monitor.ui.theme.VitalSignsForensicTheme

@ExperimentalCamera2Interop
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VitalSignsForensicTheme {
                // Monitor UI is strictly Dark Mode (Pure Black)
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    MonitorScreen()
                }
            }
        }
    }
}

@ExperimentalCamera2Interop
@Composable
fun MonitorScreen() {
    var bpm by remember { mutableStateOf<Float?>(null) }
    var spo2 by remember { mutableStateOf<Float?>(null) }
    var waveValue by remember { mutableStateOf(0f) }
    var contactScore by remember { mutableStateOf(0f) }
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        val context = LocalContext.current
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

        val processor = remember {
            PpgSignalProcessor(
                onBpmUpdate = { bpm = it },
                onSpO2Update = { spo2 = it },
                onWaveformUpdate = { waveValue = it }
            )
        }
        
        val analyzer = remember {
            PpgFrameAnalyzer { sample ->
                contactScore = sample.contactScore
                processor.process(sample)
            }
        }

        val controller = remember {
            Camera2PpgController(context, lifecycleOwner, analyzer)
        }

        DisposableEffect(Unit) {
            controller.start()
            onDispose {
                controller.stop()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FORENSIC PPG MONITOR",
                    color = Color.DarkGray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                
                // Indicador de Señal
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (contactScore > 0.6f) "SIGNAL LOCK" else "NO SIGNAL",
                        color = if (contactScore > 0.6f) Color(0xFF00FF00) else Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    LinearProgressIndicator(
                        progress = { contactScore },
                        modifier = Modifier
                            .width(60.dp)
                            .height(8.dp),
                        color = if (contactScore > 0.6f) Color(0xFF00FF00) else Color.Red,
                        trackColor = Color.DarkGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Metrics Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // BPM Metric Card
                MetricCard(
                    title = "HR",
                    unit = "bpm",
                    value = bpm?.toInt()?.toString() ?: "--",
                    color = Color(0xFF00FF00), // Verde clínico para HR
                    modifier = Modifier.weight(1f)
                )

                // SpO2 Metric Card
                MetricCard(
                    title = "SpO2",
                    unit = "%",
                    value = spo2?.toInt()?.toString() ?: "--",
                    color = Color(0xFF00FFFF), // Cian clínico para SpO2
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Waveform Graph Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
                    .background(Color(0xFF0A0A0A), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                if (contactScore > 0.6f) {
                    PpgWaveRenderer(
                        waveValue = waveValue,
                        waveColor = Color(0xFF00FF00),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "PLACE FINGER OVER CAMERA AND FLASH\nPRESS GENTLY",
                        color = Color.Red,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission required.", color = Color.White)
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    unit: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF111111), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = title,
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = unit,
                color = color.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = value,
            color = color,
            fontSize = 72.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 72.sp
        )
    }
}
