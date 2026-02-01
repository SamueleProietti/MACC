package it.sapienza.forestanimalsgame.ui.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

// --- EFFETTO PIOGGIA ---
@Composable
fun RainOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "rain_anim")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rain_tick"
    )

    // Generiamo le gocce una volta sola
    val drops = remember { List(150) { Random.nextFloat() to Random.nextFloat() } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drops.forEach { (xSeed, ySeed) ->
            val y = ((ySeed + time) % 1f) * h
            val x = xSeed * w

            drawLine(
                color = Color(0xAA88CCFF),
                start = Offset(x, y),
                end = Offset(x, y + 25f),
                strokeWidth = 3f
            )
        }
    }
}

// --- EFFETTO NEVE ---
@Composable
fun SnowOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "snow_anim")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "snow_tick"
    )

    val flakes = remember { List(100) { Random.nextFloat() to Random.nextFloat() } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        flakes.forEach { (xSeed, ySeed) ->
            val y = ((ySeed + time) % 1f) * h
            val wobble = sin(time * 6.28f + ySeed * 10) * 10f
            val x = (xSeed * w) + wobble

            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = 4f + (xSeed * 2f),
                center = Offset(x, y)
            )
        }
    }
}

// --- EFFETTO NOTTE/FILTRO ---
@Composable
fun DayNightOverlay(isNight: Boolean) {
    if (isNight) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = Color(0xFF000022).copy(alpha = 0.5f)) // Blu notte scuro
        }
    }
}