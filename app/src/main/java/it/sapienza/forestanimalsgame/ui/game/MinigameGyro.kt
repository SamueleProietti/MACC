package it.sapienza.forestanimalsgame.ui.game

import android.hardware.Sensor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import it.sapienza.forestanimalsgame.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun MinigameGyro(
    onDismiss: () -> Unit,
    onWin: () -> Unit,
    avatarResId: Int // Riceve l'avatar corretto
) {
    // 1. Sensore
    val (sensorX, _, _) = rememberSensorData(Sensor.TYPE_ACCELEROMETER)

    // 2. Stato
    var progress by remember { mutableFloatStateOf(0f) }
    var lives by remember { mutableIntStateOf(3) }
    var isRunning by remember { mutableStateOf(true) }
    var showWinDialog by remember { mutableStateOf(false) }
    var showLoseDialog by remember { mutableStateOf(false) }
    var playerPos by remember { mutableFloatStateOf(0.5f) }
    val obstacles = remember { mutableStateListOf<Pair<Float, Float>>() }
    var isInvincible by remember { mutableStateOf(false) }

    // 3. Game Loop
    LaunchedEffect(isRunning) {
        val baseSpeed = 0.004f

        while (isRunning) {
            val dt = 16L
            progress += 0.0008f
            if (progress >= 1f) {
                isRunning = false
                showWinDialog = true
            }

            if (Random.nextFloat() < 0.02f) {
                obstacles.add(Pair(Random.nextFloat(), -0.1f))
            }

            val iterator = obstacles.listIterator()
            while (iterator.hasNext()) {
                val (ox, oy) = iterator.next()
                val currentSpeed = baseSpeed + (progress * 0.005f)
                val newY = oy + currentSpeed

                if (newY > 1.1f) {
                    iterator.remove()
                } else {
                    iterator.set(Pair(ox, newY))
                    if (!isInvincible && newY > 0.78f && newY < 0.88f) {
                        if (abs(ox - playerPos) < 0.12f) {
                            lives -= 1
                            isInvincible = true
                            if (lives <= 0) {
                                isRunning = false
                                showLoseDialog = true
                            }
                        }
                    }
                }
            }
            delay(dt)
        }
    }

    LaunchedEffect(isInvincible) {
        if (isInvincible) {
            delay(1500)
            isInvincible = false
        }
    }

    LaunchedEffect(sensorX) {
        if (isRunning) {
            val targetPos = playerPos - (sensorX * 0.004f)
            playerPos = targetPos.coerceIn(0.1f, 0.9f)
        }
    }

    // --- UI ---
    Dialog(
        onDismissRequest = { if (!isRunning && !showWinDialog && !showLoseDialog) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // Full screen orizzontale
            decorFitsSystemWindows = false // Full screen verticale (sotto le barre)
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black) // Sfondo nero di sicurezza per evitare trasparenze
        ) {
            val w = maxWidth
            val h = maxHeight

            // 1. STRADA SCORREVOLE (TUTTO SCHERMO)
            Box(modifier = Modifier.fillMaxSize()) {
                ScrollingRoad(R.drawable.imagepath)
            }

            // 2. OSTACOLI
            obstacles.forEach { (ox, oy) ->
                val obstacleX = (w * ox) - 25.dp
                val obstacleY = h * oy

                Image(
                    painter = painterResource(id = R.drawable.roccia),
                    contentDescription = null,
                    modifier = Modifier
                        .offset(x = obstacleX, y = obstacleY)
                        .size(50.dp)
                )
            }

            // 3. GIOCATORE (Usa avatarResId passato da GameScreen)
            val playerX = (w * playerPos) - 40.dp
            val playerY = h * 0.8f
            val alpha = if (isInvincible && System.currentTimeMillis() % 200 < 100) 0.5f else 1f

            Image(
                painter = painterResource(id = avatarResId),
                contentDescription = null,
                modifier = Modifier
                    .offset(x = playerX, y = playerY)
                    .size(80.dp)
                    .alpha(alpha)
                    .rotate(-sensorX * 2)
            )

            // 4. HUD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding() // Sposta giù sotto la fotocamera
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { i ->
                        Icon(
                            imageVector = if (i < lives) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .width(200.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = Color.Yellow,
                    trackColor = Color.Black.copy(alpha=0.5f)
                )
            }

            // 5. TASTO ARRENDITI
            if (isRunning) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White.copy(alpha=0.8f),
                        contentColor = Color.Black
                    )
                ) { Text("Give Up (Exit)") }
            }
        }
    }

    // Dialoghi Fine Gioco
    if (showLoseDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("You crashed!") },
            text = { Text("You hit too many rocks. You must restart the game.") },
            confirmButton = {
                Button(onClick = {
                    lives = 3; progress = 0f; obstacles.clear(); playerPos = 0.5f; showLoseDialog = false; isRunning = true
                }) { Text("Retry") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Exit") } }
        )
    }

    if (showWinDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Path Completed!") },
            text = { Text("You've reached the end of the path!\n\n+1 Key obtained.") },
            confirmButton = {
                Button(onClick = { showWinDialog = false; onWin() }) { Text("Collect Key") }
            }
        )
    }
}

// --- HELPERS SCORRIMENTO ---

@Composable
fun ScrollingRoad(resId: Int) {
    val ctx = LocalContext.current
    val imgBitmap = remember(resId) { ImageBitmap.imageResource(ctx.resources, resId) }

    val infiniteTransition = rememberInfiniteTransition(label = "road_scroll")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = imgBitmap.height.toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "scroll_value"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val tileW = size.width.toInt() // Larghezza schermo intero
        val tileH = imgBitmap.height
        val rows = (size.height / tileH).toInt() + 2

        for (r in -2..rows + 1) {
            val y = (r * tileH) + offset

            drawImage(
                image = imgBitmap,
                dstOffset = IntOffset(0, y.toInt()),
                dstSize = IntSize(tileW, tileH)
            )
        }
    }
}