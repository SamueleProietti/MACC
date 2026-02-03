package it.sapienza.forestanimalsgame.ui.game

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun MinigameCompass(
    onDismiss: () -> Unit,
    onWin: () -> Unit
) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    var azimuth by remember { mutableFloatStateOf(0f) }

    // Logic Sensori
    DisposableEffect(Unit) {
        val accelerometerReading = FloatArray(3)
        val magnetometerReading = FloatArray(3)
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                }

                if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    val degrees = (Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + 360) % 360
                    azimuth = azimuth * 0.9f + degrees * 0.1f
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(listener, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME)

        onDispose { sensorManager.unregisterListener(listener) }
    }

    // Stato di Gioco
    var stage by remember { mutableIntStateOf(0) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var showWinDialog by remember { mutableStateOf(false) }

    // TIMER
    var timeLeft by remember { mutableIntStateOf(20) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(true) }

    val targetDegree = when(stage) {
        0 -> 0f; 1 -> 90f; 2 -> 270f; else -> 0f
    }
    val targetName = when(stage) {
        0 -> "NORD"; 1 -> "EST"; 2 -> "OVEST"; else -> "LIBERO"
    }

    // Loop Timer
    LaunchedEffect(isRunning) {
        while (isRunning && timeLeft > 0 && !showWinDialog) {
            delay(1000L)
            timeLeft--
            if (timeLeft == 0) {
                isRunning = false
                showTimeoutDialog = true
            }
        }
    }

    // Loop Gioco
    LaunchedEffect(azimuth, stage, isRunning) {
        if (stage < 3 && isRunning) {
            val diff = abs(azimuth - targetDegree)
            val minDiff = if (diff > 180) 360 - diff else diff

            if (minDiff < 15f) {
                holdProgress += 0.02f
                if (holdProgress >= 1f) {
                    holdProgress = 0f
                    stage++
                    // Bonus tempo quando trovi una direzione!
                    timeLeft = (timeLeft + 5).coerceAtMost(30)
                    if (stage >= 3) {
                        showWinDialog = true
                        isRunning = false
                    }
                }
            } else {
                holdProgress = (holdProgress - 0.01f).coerceAtLeast(0f)
            }
        }
    }

    // UI
    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(colors = listOf(Color.DarkGray.copy(alpha = 0.9f - (stage * 0.2f)), Color.Black))),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // TIMER
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .background(if(timeLeft <= 5) Color.Red else Color.Gray, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "$timeLeft s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }

                Text("La Nebbia Magica", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                if (stage < 3) {
                    Text("Ruota il telefono verso:", color = Color.LightGray, style = MaterialTheme.typography.bodyLarge)
                    Text(targetName, color = Color(0xFF64B5F6), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    Text("Nebbia Diradata!", color = Color.Green, style = MaterialTheme.typography.titleLarge)
                }

                // BUSSOLA
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color.DarkGray, style = Stroke(width = 20f))
                        for (i in 0 until 360 step 45) {
                            val angleRad = Math.toRadians(i.toDouble())
                            val startX = center.x + (size.width / 2 - 40) * Math.sin(angleRad).toFloat()
                            val startY = center.y - (size.width / 2 - 40) * Math.cos(angleRad).toFloat()
                            val endX = center.x + (size.width / 2 - 10) * Math.sin(angleRad).toFloat()
                            val endY = center.y - (size.width / 2 - 10) * Math.cos(angleRad).toFloat()
                            drawLine(color = if(i == 0) Color.Red else Color.LightGray, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = if(i % 90 == 0) 8f else 4f)
                        }
                    }
                    val rotationAnim by animateFloatAsState(targetValue = -azimuth)
                    Box(modifier = Modifier.fillMaxSize().rotate(rotationAnim)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val path = Path().apply { moveTo(center.x, center.y - 120f); lineTo(center.x + 30f, center.y); lineTo(center.x - 30f, center.y); close() }
                            drawPath(path, Color.Red)
                            val pathS = Path().apply { moveTo(center.x, center.y + 120f); lineTo(center.x + 30f, center.y); lineTo(center.x - 30f, center.y); close() }
                            drawPath(pathS, Color.LightGray)
                        }
                    }
                    Icon(imageVector = Icons.Filled.Explore, contentDescription = null, tint = Color.Yellow.copy(alpha=0.5f), modifier = Modifier.size(60.dp).align(Alignment.TopCenter).padding(top = 10.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))
                if (stage < 3) {
                    LinearProgressIndicator(progress = { holdProgress }, modifier = Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)), color = Color.Cyan, trackColor = Color.DarkGray)
                }
                Spacer(modifier = Modifier.height(32.dp))

                if (!showTimeoutDialog && !showWinDialog) {
                    OutlinedButton(onClick = onDismiss, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Arrenditi") }
                }
            }
        }
    }

    if (showWinDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("🌟 Orientamento Perfetto!") },
            text = { Text("Hai seguito gli spiriti e la nebbia è svanita.\n\n+1 Chiave ottenuta.") },
            confirmButton = { Button(onClick = { showWinDialog = false; onWin() }) { Text("Raccogli Chiave") } }
        )
    }

    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("⌛ Tempo Scaduto!") },
            text = { Text("La nebbia ti ha avvolto.") },
            confirmButton = { Button(onClick = { timeLeft = 20; stage = 0; holdProgress = 0f; showTimeoutDialog = false; isRunning = true }) { Text("Riprova") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Esci") } }
        )
    }
}