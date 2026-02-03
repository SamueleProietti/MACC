package it.sapienza.forestanimalsgame.ui.game

import android.hardware.Sensor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.sapienza.forestanimalsgame.R
import kotlinx.coroutines.delay
import kotlin.math.sqrt

@Composable
fun MinigameShake(
    onDismiss: () -> Unit,
    onWin: () -> Unit
) {
    // 1. Sensori
    val (x, y, z) = rememberSensorData(Sensor.TYPE_ACCELEROMETER)
    val acceleration = sqrt(x*x + y*y + z*z)

    // Stato
    var progress by remember { mutableFloatStateOf(0f) }
    var isWon by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    // TIMER
    var timeLeft by remember { mutableIntStateOf(20) } // 20 Secondi
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(true) }

    // Logic Timer
    LaunchedEffect(isRunning) {
        while (isRunning && timeLeft > 0 && !isWon) {
            delay(1000L)
            timeLeft--
            if (timeLeft == 0) {
                isRunning = false
                showTimeoutDialog = true
            }
        }
    }

    // Rilevamento Scossa
    LaunchedEffect(acceleration) {
        if (isRunning && !isWon && acceleration > 14f) {
            progress += 0.05f
            if (progress >= 1f) {
                progress = 1f
                isWon = true
                isRunning = false
                showSuccessDialog = true
            }
        }
    }

    val shakeAnim by animateFloatAsState(targetValue = if (acceleration > 12f) (x * 2f) else 0f, label = "shake")

    // UI Overlay
    Dialog(
        onDismissRequest = { if (!isWon && !showTimeoutDialog) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // TIMER DISPLAY
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

                Text(
                    text = "L'Albero Antico",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Scuoti forte il telefono!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
                )

                Image(
                    painter = painterResource(id = R.drawable.tree),
                    contentDescription = "Albero",
                    modifier = Modifier
                        .size(250.dp)
                        .rotate(shakeAnim)
                        .scale(1f + (progress * 0.2f))
                )

                Spacer(modifier = Modifier.height(48.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    color = Color(0xFFFFD54F),
                    trackColor = Color.DarkGray
                )

                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (!isWon && !showTimeoutDialog) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Arrenditi (Esci)")
                    }
                }
            }
        }
    }

    // DIALOG VITTORIA
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("🌳 Missione Compiuta!") },
            text = { Text("Hai scosso l'albero con forza e la Chiave Magica è caduta!\n\n+1 Chiave aggiunta all'inventario.") },
            confirmButton = {
                Button(onClick = {
                    showSuccessDialog = false
                    onWin()
                }) {
                    Text("Raccogli Chiave")
                }
            }
        )
    }

    // DIALOG SCONFITTA (TEMPO SCADUTO)
    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("⌛ Tempo Scaduto!") },
            text = { Text("Non sei riuscito a far cadere la chiave in tempo.") },
            confirmButton = {
                Button(onClick = {
                    // Reset
                    timeLeft = 20
                    progress = 0f
                    showTimeoutDialog = false
                    isRunning = true
                }) {
                    Text("Riprova")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Esci") }
            }
        )
    }
}