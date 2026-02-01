package it.sapienza.forestanimalsgame.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.forestanimalsgame.R
import it.sapienza.forestanimalsgame.data.model.GameState
import it.sapienza.forestanimalsgame.data.model.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.hypot
import kotlin.math.max

// --- CONFIGURAZIONE ---
const val DEBUG_MODE = false // Metti true se vuoi vedere i cerchi di debug
const val AVATAR_SIZE_PX = 150f
// Calibrazione: Quanto spostare l'immagine affinché i "piedi" siano sul punto cliccato
const val DRAW_OFFSET_Y = 15f

private val OffsetSaver: Saver<Offset, ArrayList<Float>> = Saver(
    save = { arrayListOf(it.x, it.y) },
    restore = { Offset(it[0], it[1]) }
)

private val StringSetSaver: Saver<Set<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it) },
    restore = { it.toSet() }
)

private enum class QuestType { LIGHT, GYRO, CAMERA }

private data class QuestSpot(
    val id: String,
    val title: String,
    val description: String,
    val type: QuestType,
    val position: Offset,
    val radiusPx: Float = 100f
)

private interface DrawableItem {
    val y: Float
    fun draw(scope: DrawScope)
}

@Composable
fun GameScreen(
    sessionId: String,
    session: Session?,
    avatarId: String,
    initialGameState: GameState?,
    currentWeather: String = "clear",
    isNightTime: Boolean = false,
    onAutoSave: (GameState) -> Unit,
    onStop: (GameState) -> Unit,
    onLeave: () -> Unit
) {
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "unknown" }

    // 1. SETUP MONDO
    val worldSize = remember { Size(width = 4000f, height = 4000f) }
    val worldCenter = remember(worldSize) { Offset(worldSize.width / 2f, worldSize.height / 2f) }
    val spawn = remember(currentUid, worldCenter) { uidToSpawn(currentUid, worldCenter) }

    // 2. RISORSE
    val context = LocalContext.current
    val resId = remember(avatarId) { avatarResId(avatarId) }
    val avatarBitmap = remember(resId) { ImageBitmap.imageResource(context.resources, resId) }

    val terrainTile = rememberTerrainTile()
    val pathTile = rememberPathTile()
    val forestResources = rememberForestResources()
    val signBitmap = forestResources[SceneryType.SIGN]!!

    val staticScenery = remember(worldSize) { generateForestScenery(worldSize, 250) }

    // 3. STATO
    // Zoom di default a 1.5f per partire vicini
    val zoomState = rememberSaveable { mutableStateOf(1f) }
    val panState = rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }

    var canvasSize by remember { mutableStateOf(IntSize(0, 0)) }

    var avatar by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(spawn) }
    var target by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(spawn) }

    var selectedQuest by remember { mutableStateOf<QuestSpot?>(null) }
    var activeQuestId by rememberSaveable { mutableStateOf<String?>(null) }
    var completed by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(setOf<String>()) }

    val questSpots = remember {
        listOf(
            QuestSpot("q_light", "La Radura Oscura", "Trova la radura", QuestType.LIGHT, Offset(600f, 600f)),
            QuestSpot("q_gyro", "L'Albero Caduto", "Mantieni l'equilibrio", QuestType.GYRO, Offset(1500f, 800f)),
            QuestSpot("q_camera", "Il Grande Fungo", "Scatta una foto", QuestType.CAMERA, Offset(1000f, 2000f))
        )
    }

    var testWeather by remember { mutableStateOf(currentWeather) }
    var testNight by remember { mutableStateOf(isNightTime) }

    val vividFilter = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.4f) }) }

    // HYDRATE
    var hydrated by remember(sessionId, currentUid) { mutableStateOf(false) }
    LaunchedEffect(sessionId, currentUid, initialGameState) {
        if (hydrated) return@LaunchedEffect
        val gs = initialGameState
        if (gs != null) {
            avatar = clampCenter(Offset(gs.avatarX.toFloat(), gs.avatarY.toFloat()), worldSize.width, worldSize.height)
            target = clampCenter(Offset(gs.targetX.toFloat(), gs.targetY.toFloat()), worldSize.width, worldSize.height)
            activeQuestId = gs.activeQuestId
            completed = gs.completed.toSet()
            zoomState.value = gs.zoom.toFloat()
            panState.value = Offset(gs.panX.toFloat(), gs.panY.toFloat())
        }
        hydrated = true
    }

    // GESTIONE LIMITI ZOOM & PAN
    // Appena cambia la dimensione dello schermo o lo zoom, ricalcoliamo il pan sicuro
    LaunchedEffect(canvasSize, zoomState.value) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            val safePan = clampPanStrict(panState.value, canvasSize.width.toFloat(), canvasSize.height.toFloat(), worldSize.width, worldSize.height, zoomState.value)
            if (safePan != panState.value) panState.value = safePan
        }
    }

    // Inizializza camera al centro avatar al primo avvio
    var cameraInitialized by remember(sessionId) { mutableStateOf(false) }
    LaunchedEffect(canvasSize, sessionId) {
        if (!cameraInitialized && canvasSize.width > 0) {
            val desired = Offset(
                x = canvasSize.width / 2f - avatar.x * zoomState.value,
                y = canvasSize.height / 2f - avatar.y * zoomState.value
            )
            panState.value = clampPanStrict(desired, canvasSize.width.toFloat(), canvasSize.height.toFloat(), worldSize.width, worldSize.height, zoomState.value)
            cameraInitialized = true
        }
    }

    // MOVEMENT LOOP
    LaunchedEffect(target) {
        while (true) {
            val dx = target.x - avatar.x
            val dy = target.y - avatar.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val speed = 10f

            if (dist < speed) {
                avatar = target
                break
            }
            val stepX = (dx / dist) * speed
            val stepY = (dy / dist) * speed

            avatar = clampCenter(Offset(avatar.x + stepX, avatar.y + stepY), worldSize.width, worldSize.height)
            delay(16)
        }
    }

    LaunchedEffect(avatar, activeQuestId) {
        val qid = activeQuestId ?: return@LaunchedEffect
        val spot = questSpots.firstOrNull { it.id == qid } ?: return@LaunchedEffect
        val d = hypot((avatar.x - spot.position.x).toDouble(), (avatar.y - spot.position.y).toDouble()).toFloat()
        if (d <= spot.radiusPx) {
            completed = completed + qid
            activeQuestId = null
        }
    }

    LaunchedEffect(sessionId, currentUid) {
        snapshotFlow {
            listOf(avatar.x, avatar.y, target.x, target.y, zoomState.value, panState.value.x, panState.value.y, activeQuestId, completed.size)
        }
            .distinctUntilChanged()
            .debounce(1000)
            .collect {
                onAutoSave(
                    GameState(
                        avatarX = avatar.x.toDouble(), avatarY = avatar.y.toDouble(),
                        targetX = target.x.toDouble(), targetY = target.y.toDouble(),
                        activeQuestId = activeQuestId, completed = completed.toList().sorted(),
                        panX = panState.value.x.toDouble(), panY = panState.value.y.toDouble(), zoom = zoomState.value.toDouble(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
    }

    // --- UI LAYOUT ---
    // Usiamo Box come contenitore principale per sovrapporre Mappa e UI
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. MAPPA (Sotto tutto)
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF2E7D32)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, panChange, zoomChange, _ ->
                            val oldZoom = zoomState.value
                            val currentPan = panState.value

                            val cw = canvasSize.width.toFloat()
                            val ch = canvasSize.height.toFloat()

                            // CALCOLO MIN ZOOM DINAMICO
                            // Lo zoom minimo deve essere tale che worldSize * zoom >= screenSize
                            // Quindi zoom >= screenSize / worldSize
                            val minZoomX = cw / worldSize.width
                            val minZoomY = ch / worldSize.height
                            val calculatedMinZoom = max(minZoomX, minZoomY)
                            // Max zoom fisso a 3.5x
                            val calculatedMaxZoom = 3.5f

                            // Se lo schermo è ridimensionato, assicurati che il currentZoom non sia illegale
                            val safeOldZoom = oldZoom.coerceIn(calculatedMinZoom, calculatedMaxZoom)

                            val newZoom = (safeOldZoom * zoomChange).coerceIn(calculatedMinZoom, calculatedMaxZoom)
                            val zoomFactor = newZoom / safeOldZoom
                            val tentativePan = currentPan + panChange + (centroid - currentPan) * (1 - zoomFactor)

                            zoomState.value = newZoom
                            panState.value = clampPanStrict(tentativePan, cw, ch, worldSize.width, worldSize.height, newZoom)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val currentZoom = zoomState.value
                            val currentPan = panState.value

                            val worldTap = (tap - currentPan) / currentZoom

                            val hitQuest = questSpots.firstOrNull { spot ->
                                hypot((worldTap.x - spot.position.x).toDouble(), (worldTap.y - spot.position.y).toDouble()) <= spot.radiusPx
                            }

                            if (hitQuest != null) {
                                selectedQuest = hitQuest
                            } else {
                                target = clampCenter(worldTap, worldSize.width, worldSize.height)
                            }
                        }
                    }
            ) {
                if (size.width > 0 && size.height > 0) {
                    canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                }

                val currentZoom = zoomState.value
                val currentPan = panState.value

                withTransform({
                    translate(currentPan.x, currentPan.y)
                    scale(currentZoom, currentZoom)
                }) {
                    val viewLeft = -currentPan.x / currentZoom
                    val viewTop = -currentPan.y / currentZoom
                    val viewRight = viewLeft + size.width / currentZoom
                    val viewBottom = viewTop + size.height / currentZoom
                    val visibleRect = androidx.compose.ui.geometry.Rect(viewLeft, viewTop, viewRight, viewBottom)

                    drawTerrain(worldSize, terrainTile, visibleRect)
                    val pathPoints = questSpots.map { it.position }
                    drawPaths(pathPoints, pathTile)

                    val drawQueue = ArrayList<DrawableItem>()

                    // Scenery
                    staticScenery.forEach { obj ->
                        // Culling con margine più ampio
                        if (obj.x in (viewLeft - 400)..(viewRight + 400) &&
                            obj.y in (viewTop - 400)..(viewBottom + 400)) {
                            val bmp = forestResources[obj.type]
                            if (bmp != null) {
                                drawQueue.add(object : DrawableItem {
                                    override val y = obj.y
                                    override fun draw(scope: DrawScope) {
                                        val w = bmp.width * obj.scale
                                        val h = bmp.height * obj.scale
                                        scope.drawImage(image = bmp, dstOffset = IntOffset((obj.x - w/2).toInt(), (obj.y - h).toInt()), dstSize = IntSize(w.toInt(), h.toInt()), colorFilter = vividFilter)
                                    }
                                })
                            }
                        }
                    }

                    // Quest
                    questSpots.forEach { quest ->
                        drawQueue.add(object : DrawableItem {
                            override val y = quest.position.y
                            override fun draw(scope: DrawScope) {
                                val isCompleted = quest.id in completed
                                val alpha = if (isCompleted) 0.5f else 1f
                                val scale = 3.5f
                                val w = signBitmap.width.toFloat() * scale
                                val h = signBitmap.height.toFloat() * scale
                                scope.drawImage(image = signBitmap, dstOffset = IntOffset((quest.position.x - w/2).toInt(), (quest.position.y - h).toInt()), dstSize = IntSize(w.toInt(), h.toInt()), alpha = alpha)
                            }
                        })
                    }

                    // Avatar
                    drawQueue.add(object : DrawableItem {
                        override val y = avatar.y + 20f
                        override fun draw(scope: DrawScope) {
                            scope.drawImage(
                                image = avatarBitmap,
                                // Offset Y per centrare i piedi sul punto
                                dstOffset = IntOffset(
                                    (avatar.x - AVATAR_SIZE_PX/2).toInt(),
                                    (avatar.y - AVATAR_SIZE_PX + DRAW_OFFSET_Y).toInt()
                                ),
                                dstSize = IntSize(AVATAR_SIZE_PX.toInt(), AVATAR_SIZE_PX.toInt())
                            )
                        }
                    })

                    // Players
                    session?.members?.forEach { m ->
                        if (m.uid != currentUid) {
                            val pos = uidToSpawn(m.uid, worldCenter)
                            drawQueue.add(object : DrawableItem {
                                override val y = pos.y + 20f
                                override fun draw(scope: DrawScope) {
                                    scope.drawImage(image = avatarBitmap, dstOffset = IntOffset((pos.x - AVATAR_SIZE_PX/2).toInt(), (pos.y - AVATAR_SIZE_PX + DRAW_OFFSET_Y).toInt()), dstSize = IntSize(AVATAR_SIZE_PX.toInt(), AVATAR_SIZE_PX.toInt()), alpha = 0.6f)
                                }
                            })
                        }
                    }

                    drawQueue.sortBy { it.y }
                    drawQueue.forEach { it.draw(this) }

                    if (DEBUG_MODE) {
                        drawCircle(Color.Red, radius = 10f, center = target, style = Stroke(width=3f))
                        drawCircle(Color.Blue, radius = 15f, center = avatar, style = Stroke(width=3f))
                        drawLine(Color.Green, start = avatar, end = target, strokeWidth = 2f)
                    }
                }
            }
        }

        // 2. LAYER METEO (Sopra mappa, sotto UI)
        // Ignorano il padding di sistema per coprire tutto
        when (testWeather) {
            "rain" -> RainOverlay()
            "snow" -> SnowOverlay()
        }
        DayNightOverlay(isNight = testNight)

        // 3. UI OVERLAY (Pulsanti e HUD)
        // ✅ RESPONSIVE FIX: systemBarsPadding() sposta la UI sotto la status bar/notch
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding() // Questo evita che la UI finisca sotto la fotocamera o nav bar
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Sinistra: Info e Orario
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // HUD Orario
                    TimeHUD(testNight)

                    // Info Testo (opzionale)
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha=0.8f))) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Foresta", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Status: ${session?.status ?: "..."}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Destra: Meteo e Controlli
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                    WeatherHUD(testWeather)

                    // Bottoni Test (Raggruppati)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = {
                                testWeather = when(testWeather) { "clear" -> "rain"; "rain" -> "snow"; else -> "clear" }
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.width(60.dp)
                        ) { Text("W") } // W = Weather

                        Button(
                            onClick = { testNight = !testNight },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.width(60.dp)
                        ) { Text("T") } // T = Time

                        Button(
                            onClick = onLeave,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.width(60.dp)
                        ) { Text("X") }
                    }
                }
            }

            // BOTTOM BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedButton(
                    onClick = {
                        avatar = spawn; target = spawn; zoomState.value = 1.3f
                    },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha=0.9f))
                ) { Text("Reset") }

                Button(
                    onClick = {
                        val next = questSpots.firstOrNull { it.id !in completed }
                        if (next != null) { activeQuestId = next.id; target = next.position }
                    },
                    enabled = activeQuestId == null && completed.size < questSpots.size
                ) { Text("Nuova Missione") }
            }
        }
    }

    // Dialogs
    val sq = selectedQuest
    if (sq != null) {
        AlertDialog(
            onDismissRequest = { selectedQuest = null },
            title = { Text(sq.title) },
            text = { Text(sq.description) },
            confirmButton = { Button(onClick = { activeQuestId = sq.id; target = sq.position; selectedQuest = null }) { Text("Attiva") } },
            dismissButton = { TextButton(onClick = { selectedQuest = null }) { Text("Chiudi") } }
        )
    }
}

// ... Huds & Helpers ...
@Composable
fun TimeHUD(isNight: Boolean) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f))) {
        Row(modifier = Modifier.padding(12.dp, 8.dp)) {
            Icon(imageVector = if (isNight) Icons.Filled.Bedtime else Icons.Filled.WbSunny, contentDescription = null, tint = if (isNight) Color(0xFF90CAF9) else Color(0xFFFFEB3B), modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun WeatherHUD(weather: String) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f))) {
        Row(modifier = Modifier.padding(12.dp, 8.dp)) {
            val (icon, color) = when (weather) {
                "rain" -> Icons.Filled.WaterDrop to Color(0xFF4FC3F7)
                "snow" -> Icons.Filled.AcUnit to Color.White
                "clear" -> Icons.Filled.WbSunny to Color(0xFFFFD54F)
                else -> Icons.Filled.Cloud to Color.LightGray
            }
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        }
    }
}

private fun uidToSpawn(uid: String, center: Offset): Offset {
    val h = uid.hashCode()
    val dx = ((h % 401) - 200).toFloat()
    val dy = (((h / 7) % 401) - 200).toFloat()
    return Offset(center.x + dx, center.y + dy)
}

private fun avatarResId(avatarId: String): Int = when (avatarId.lowercase()) {
    "fox" -> R.drawable.av_fox
    "deer" -> R.drawable.av_deer
    "wolf" -> R.drawable.av_wolf
    "bear" -> R.drawable.av_bear
    "boar" -> R.drawable.av_boar
    else -> R.drawable.av_fox
}

private fun clampCenter(p: Offset, width: Float, height: Float): Offset {
    return Offset(p.x.coerceIn(0f, width), p.y.coerceIn(0f, height))
}

private fun clampPanStrict(pan: Offset, canvasW: Float, canvasH: Float, worldW: Float, worldH: Float, zoom: Float): Offset {
    val scaledWorldW = worldW * zoom
    val scaledWorldH = worldH * zoom

    // LOGICA DI CLAMPING RIGIDO:
    // Se la mappa è più grande dello schermo (normale), il pan deve stare tra [canvasW - scaledW, 0]
    // Se la mappa è più piccola (solo se minZoom è calcolato male, ma qui lo preveniamo), centra la mappa.

    val minX = if (scaledWorldW > canvasW) canvasW - scaledWorldW else (canvasW - scaledWorldW) / 2
    val maxX = if (scaledWorldW > canvasW) 0f else (canvasW - scaledWorldW) / 2

    val minY = if (scaledWorldH > canvasH) canvasH - scaledWorldH else (canvasH - scaledWorldH) / 2
    val maxY = if (scaledWorldH > canvasH) 0f else (canvasH - scaledWorldH) / 2

    return Offset(
        pan.x.coerceIn(minX, maxX),
        pan.y.coerceIn(minY, maxY)
    )
}