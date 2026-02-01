package it.sapienza.forestanimalsgame.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
const val DEBUG_MODE = false
const val AVATAR_SIZE_PX = 150f
const val DRAW_OFFSET_Y = 15f
const val FIXED_ZOOM = 1f

private val OffsetSaver: Saver<Offset, ArrayList<Float>> = Saver(
    save = { arrayListOf(it.x, it.y) },
    restore = { Offset(it[0], it[1]) }
)

private val StringSetSaver: Saver<Set<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it) },
    restore = { it.toSet() }
)

enum class QuestType { LIGHT, GYRO, CAMERA }

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
    val zoomState = rememberSaveable { mutableStateOf(FIXED_ZOOM) }
    val panState = rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize(0, 0)) }

    var avatar by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(spawn) }
    var target by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(spawn) }

    // STATO MISSIONI & CHIAVI
    var selectedQuest by remember { mutableStateOf<QuestSpot?>(null) }
    var activeQuestId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeMinigame by remember { mutableStateOf<QuestType?>(null) }

    var completed by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(setOf<String>()) }
    var collectedKeys by rememberSaveable { mutableIntStateOf(0) }

    val isGameWon = collectedKeys >= 3

    var showIntroDialog by rememberSaveable { mutableStateOf(true) }
    var showWinDialog by rememberSaveable { mutableStateOf(false) }
    var showCageDialog by remember { mutableStateOf(false) }
    var showBearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(collectedKeys) {
        if (collectedKeys > 0) showIntroDialog = false
        if (collectedKeys == 3) showWinDialog = true
    }

    val questSpots = remember {
        listOf(
            QuestSpot("q_light", "La Bussola Magica", "Trova il Nord per diradare la nebbia", QuestType.LIGHT, Offset(600f, 600f)),
            QuestSpot("q_gyro", "Il Sentiero Tortuoso", "Guida l'animale inclinando il telefono", QuestType.GYRO, Offset(1500f, 800f)),
            QuestSpot("q_accel", "L'Albero Antico", "Scuoti l'albero per far cadere la chiave", QuestType.CAMERA, Offset(1000f, 2000f))
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
            zoomState.value = FIXED_ZOOM
            panState.value = Offset(gs.panX.toFloat(), gs.panY.toFloat())
        }
        hydrated = true
    }

    LaunchedEffect(canvasSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            val safePan = clampPanStrict(panState.value, canvasSize.width.toFloat(), canvasSize.height.toFloat(), worldSize.width, worldSize.height, FIXED_ZOOM)
            if (safePan != panState.value) panState.value = safePan
        }
    }

    var cameraInitialized by remember(sessionId) { mutableStateOf(false) }
    LaunchedEffect(canvasSize, sessionId) {
        if (!cameraInitialized && canvasSize.width > 0) {
            val desired = Offset(
                x = canvasSize.width / 2f - avatar.x * FIXED_ZOOM,
                y = canvasSize.height / 2f - avatar.y * FIXED_ZOOM
            )
            panState.value = clampPanStrict(desired, canvasSize.width.toFloat(), canvasSize.height.toFloat(), worldSize.width, worldSize.height, FIXED_ZOOM)
            cameraInitialized = true
        }
    }

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
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. MAPPA
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF2E7D32)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, panChange, _, _ ->
                            val currentPan = panState.value
                            val cw = canvasSize.width.toFloat()
                            val ch = canvasSize.height.toFloat()
                            val tentativePan = currentPan + panChange
                            panState.value = clampPanStrict(tentativePan, cw, ch, worldSize.width, worldSize.height, FIXED_ZOOM)
                        }
                    }
                    // FIX BUG POPUP: Cambiato key da Unit a isGameWon
                    // In questo modo, quando vinci, il rilevatore di tocchi si aggiorna
                    // e capisce che ora devi cliccare l'ORSO e non più la GABBIA.
                    .pointerInput(isGameWon) {
                        detectTapGestures { tap ->
                            val currentPan = panState.value
                            val worldTap = (tap - currentPan) / FIXED_ZOOM

                            val hitQuest = questSpots.firstOrNull { spot ->
                                hypot((worldTap.x - spot.position.x).toDouble(), (worldTap.y - spot.position.y).toDouble()) <= spot.radiusPx
                            }

                            // LOGICA DI CLIC GABBIA/ORSO
                            var hitCage: SceneryObject? = null
                            var hitBear: SceneryObject? = null

                            if (!isGameWon) {
                                // Se non abbiamo vinto, cerchiamo solo la GABBIA
                                hitCage = staticScenery.firstOrNull {
                                    it.type == SceneryType.CAGE &&
                                            hypot((worldTap.x - it.x).toDouble(), (worldTap.y - it.y).toDouble()) <= 150f
                                }
                            } else {
                                // Se ABBIAMO vinto, cerchiamo solo l'ORSO
                                hitBear = staticScenery.firstOrNull {
                                    it.type == SceneryType.NPC_PRISONER &&
                                            hypot((worldTap.x - it.x).toDouble(), (worldTap.y - it.y).toDouble()) <= 150f
                                }
                            }

                            if (hitQuest != null) {
                                selectedQuest = hitQuest
                            } else if (hitCage != null) {
                                // Cliccato gabbia quando non vinto -> Dialogo "Ti servono chiavi"
                                showCageDialog = true
                            } else if (hitBear != null) {
                                // Cliccato orso quando vinto -> Dialogo "Grazie!"
                                showBearDialog = true
                            } else {
                                target = clampCenter(worldTap, worldSize.width, worldSize.height)
                            }
                        }
                    }
            ) {
                if (size.width > 0 && size.height > 0) {
                    canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                }

                val currentPan = panState.value

                withTransform({
                    translate(currentPan.x, currentPan.y)
                    scale(FIXED_ZOOM, FIXED_ZOOM)
                }) {
                    val viewLeft = -currentPan.x / FIXED_ZOOM
                    val viewTop = -currentPan.y / FIXED_ZOOM
                    val viewRight = viewLeft + size.width / FIXED_ZOOM
                    val viewBottom = viewTop + size.height / FIXED_ZOOM
                    val visibleRect = androidx.compose.ui.geometry.Rect(viewLeft, viewTop, viewRight, viewBottom)

                    drawTerrain(worldSize, terrainTile, visibleRect)
                    val pathPoints = questSpots.map { it.position }
                    drawPaths(pathPoints, pathTile)

                    val drawQueue = ArrayList<DrawableItem>()

                    staticScenery.forEach { obj ->
                        if (isGameWon) {
                            if (obj.type == SceneryType.CAGE) return@forEach
                        } else {
                            if (obj.type == SceneryType.NPC_PRISONER) return@forEach
                        }

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

                    questSpots.forEach { quest ->
                        drawQueue.add(object : DrawableItem {
                            override val y = quest.position.y
                            override fun draw(scope: DrawScope) {
                                val isCompleted = quest.id in completed
                                if (isCompleted) return

                                val w = signBitmap.width.toFloat() * 3.5f
                                val h = signBitmap.height.toFloat() * 3.5f
                                scope.drawImage(image = signBitmap, dstOffset = IntOffset((quest.position.x - w/2).toInt(), (quest.position.y - h).toInt()), dstSize = IntSize(w.toInt(), h.toInt()))
                            }
                        })
                    }

                    drawQueue.add(object : DrawableItem {
                        override val y = avatar.y + 20f
                        override fun draw(scope: DrawScope) {
                            scope.drawImage(
                                image = avatarBitmap,
                                dstOffset = IntOffset(
                                    (avatar.x - AVATAR_SIZE_PX/2).toInt(),
                                    (avatar.y - AVATAR_SIZE_PX + DRAW_OFFSET_Y).toInt()
                                ),
                                dstSize = IntSize(AVATAR_SIZE_PX.toInt(), AVATAR_SIZE_PX.toInt())
                            )
                        }
                    })

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

        when (testWeather) {
            "rain" -> RainOverlay()
            "snow" -> SnowOverlay()
        }
        DayNightOverlay(isNight = testNight)

        // 3. UI OVERLAY
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeHUD(testNight)
                }
                KeysHUD(collectedKeys)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                    WeatherHUD(testWeather)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { testWeather = when(testWeather) { "clear" -> "rain"; "rain" -> "snow"; else -> "clear" } }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(60.dp)) { Text("W") }
                        Button(onClick = { testNight = !testNight }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(60.dp)) { Text("T") }
                        Button(onClick = onLeave, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), contentPadding = PaddingValues(0.dp), modifier = Modifier.width(60.dp)) { Text("X") }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                OutlinedButton(onClick = { avatar = spawn; target = spawn }, colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha=0.9f))) { Text("Reset") }

                Button(onClick = {
                    if(collectedKeys < 3) collectedKeys += 1
                }, enabled = collectedKeys < 3) { Text("+ Chiave") }
            }
        }

        // --- 4. OVERLAY MINIGIOCHI ---
        when (activeMinigame) {
            QuestType.CAMERA -> {
                MinigameShake(
                    onDismiss = { activeMinigame = null },
                    onWin = {
                        activeMinigame = null
                        collectedKeys = (collectedKeys + 1).coerceAtMost(3)
                        if (activeQuestId != null) {
                            completed = completed + activeQuestId!!
                            activeQuestId = null
                        }
                    }
                )
            }

            QuestType.GYRO -> {
                MinigameGyro(
                    onDismiss = { activeMinigame = null },
                    onWin = {
                        activeMinigame = null
                        collectedKeys = (collectedKeys + 1).coerceAtMost(3)
                        if (activeQuestId != null) {
                            completed = completed + activeQuestId!!
                            activeQuestId = null
                        }
                    },
                    avatarResId = resId
                )
            }

            QuestType.LIGHT -> {
                MinigameCompass(
                    onDismiss = { activeMinigame = null },
                    onWin = {
                        activeMinigame = null
                        collectedKeys = (collectedKeys + 1).coerceAtMost(3)
                        if (activeQuestId != null) {
                            completed = completed + activeQuestId!!
                            activeQuestId = null
                        }
                    }
                )
            }
            null -> { /* Nessun minigioco attivo */ }
        }
    }

    // --- DIALOGHI ---
    if (showIntroDialog && collectedKeys == 0) {
        AlertDialog(
            onDismissRequest = { showIntroDialog = false },
            title = { Text("Benvenuto nella Foresta!") },
            text = { Text("Il tuo amico Orso è stato catturato!\n\nDevi completare le 3 missioni dei Saggi per ottenere le Chiavi Magiche e liberarlo.") },
            confirmButton = { Button(onClick = { showIntroDialog = false }) { Text("Inizia l'Avventura") } }
        )
    }

    val sq = selectedQuest
    if (sq != null) {
        val done = sq.id in completed
        if (!done) {
            AlertDialog(
                onDismissRequest = { selectedQuest = null },
                title = { Text(sq.title) },
                text = { Text(sq.description) },
                confirmButton = {
                    Button(onClick = {
                        activeQuestId = sq.id
                        activeMinigame = sq.type
                        selectedQuest = null
                    }) { Text("Gioca") }
                },
                dismissButton = { TextButton(onClick = { selectedQuest = null }) { Text("Annulla") } }
            )
        }
    }

    if (showWinDialog) {
        AlertDialog(
            onDismissRequest = { /* Bloccato */ },
            title = { Text("🎉 L'ORSO È LIBERO! 🎉") },
            text = { Text("Hai raccolto tutte le 3 chiavi!\nLa gabbia si è aperta e il tuo amico è salvo grazie a te.") },
            confirmButton = { Button(onClick = onLeave) { Text("Torna al Menu") } },
            dismissButton = { TextButton(onClick = { showWinDialog = false }) { Text("Resta qui") } }
        )
    }

    // GABBIA CHIUSA
    if (showCageDialog) {
        AlertDialog(
            onDismissRequest = { showCageDialog = false },
            title = { Text("La Gabbia è chiusa") },
            text = { Text("Ti servono 3 Chiavi per aprirla.\nNe hai raccolte ${collectedKeys}/3.") },
            confirmButton = { TextButton(onClick = { showCageDialog = false }) { Text("Ok") } }
        )
    }

    // DIALOGO ORSO LIBERO
    if (showBearDialog) {
        AlertDialog(
            onDismissRequest = { showBearDialog = false },
            title = { Text("🐻 Grazie Amico!") },
            text = { Text("Mi hai salvato! Non dimenticherò mai il tuo aiuto.\nLa foresta è un posto migliore grazie a te.") },
            confirmButton = { TextButton(onClick = { showBearDialog = false }) { Text("Prego!") } }
        )
    }
}

// ... Huds & Helpers uguali a prima ...
@Composable
fun KeysHUD(collected: Int) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f))) {
        Row(modifier = Modifier.padding(12.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { index ->
                val active = index < collected
                Icon(imageVector = Icons.Filled.VpnKey, contentDescription = null, tint = if (active) Color(0xFFFFD54F) else Color.Gray.copy(alpha=0.5f), modifier = Modifier.size(28.dp))
            }
        }
    }
}

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
                else -> Icons.Filled.WbSunny to Color(0xFFFFD54F)
                //else -> Icons.Filled.Cloud to Color.LightGray
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
    val minX = if (scaledWorldW > canvasW) canvasW - scaledWorldW else (canvasW - scaledWorldW) / 2
    val maxX = if (scaledWorldW > canvasW) 0f else (canvasW - scaledWorldW) / 2
    val minY = if (scaledWorldH > canvasH) canvasH - scaledWorldH else (canvasH - scaledWorldH) / 2
    val maxY = if (scaledWorldH > canvasH) 0f else (canvasH - scaledWorldH) / 2
    return Offset(pan.x.coerceIn(minX, maxX), pan.y.coerceIn(minY, maxY))
}