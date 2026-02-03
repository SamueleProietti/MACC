package it.sapienza.forestanimalsgame.ui.game

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.AcUnit
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.forestanimalsgame.R
import it.sapienza.forestanimalsgame.data.model.GameState
import it.sapienza.forestanimalsgame.data.model.Session
import it.sapienza.forestanimalsgame.ui.theme.ForestButton
import it.sapienza.forestanimalsgame.ui.theme.ForestDialog
import it.sapienza.forestanimalsgame.ui.theme.AppAudio
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.hypot

// --- CONFIGURAZIONE ---
const val DEBUG_MODE = true
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
    val context = LocalContext.current

    // 1. SETUP MONDO
    val worldSize = remember { Size(width = 4000f, height = 4000f) }
    val worldCenter = remember(worldSize) { Offset(worldSize.width / 2f, worldSize.height / 2f) }
    val spawn = remember(currentUid, worldCenter) { uidToSpawn(currentUid, worldCenter) }

    // 2. RISORSE GRAFICHE
    val allAvatars = remember {
        mapOf(
            "fox" to ImageBitmap.imageResource(context.resources, R.drawable.av_fox),
            "bear" to ImageBitmap.imageResource(context.resources, R.drawable.av_bear),
            "wolf" to ImageBitmap.imageResource(context.resources, R.drawable.av_wolf),
            "boar" to ImageBitmap.imageResource(context.resources, R.drawable.av_boar),
            "deer" to ImageBitmap.imageResource(context.resources, R.drawable.av_deer)
        )
    }

    val myAvatarBitmap = allAvatars[avatarId] ?: allAvatars["fox"]!!

    val terrainTile = rememberTerrainTile()
    val pathTile = rememberPathTile()
    val forestResources = rememberForestResources()
    val signBitmap = forestResources[SceneryType.SIGN]!!

    val staticScenery = remember(worldSize) { generateForestScenery(worldSize, 250) }

    val infiniteTransition = rememberInfiniteTransition(label = "marker_anim")
    val markerBobY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f, 
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bobbing"
    )

    // 3. STATO GIOCO
    val zoomState = rememberSaveable { mutableStateOf(FIXED_ZOOM) }
    val panState = rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize(0, 0)) }

    var avatar by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(spawn) }
    var target by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(spawn) }

    // STATO MISSIONI
    var selectedQuest by remember { mutableStateOf<QuestSpot?>(null) }
    var activeQuestId by rememberSaveable { mutableStateOf<String?>(null) }
    var activeMinigame by remember { mutableStateOf<QuestType?>(null) }

    // STATO PRINCIPALE
    var completed by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(setOf<String>()) }

    // CALCOLO DERIVATO
    val collectedKeys = completed.size
    val isGameWon = collectedKeys >= 3

    // Dialoghi
    var showIntroDialog by rememberSaveable { mutableStateOf(true) }
    var showWinDialog by rememberSaveable { mutableStateOf(false) }
    var showCageDialog by remember { mutableStateOf(false) }
    var showBearDialog by remember { mutableStateOf(false) }
    var showAlreadyCollectedDialog by remember { mutableStateOf(false) }

    val currentServerState by rememberUpdatedState(initialGameState)

    // DEBUG LOOP
    LaunchedEffect(Unit) {
        while (true) {
            val serverList = currentServerState?.completed ?: emptyList()
            val localList = completed.toList()
            if (DEBUG_MODE) {
                Log.d("GAME_DEBUG", "Keys: $collectedKeys | Local: $localList | Server: $serverList")
            }
            delay(2000)
        }
    }

    // SYNC MULTIPLAYER
    LaunchedEffect(initialGameState) {
        if (initialGameState != null) {
            val serverCompleted = initialGameState.completed.toSet()
            val merged = completed + serverCompleted

            if (merged.size > completed.size) {
                completed = merged
                if (merged.size >= 3 && !showWinDialog) {
                    showWinDialog = true
                    AppAudio.playWin()
                }
            }
        }
    }

    // Gestione Intro
    LaunchedEffect(collectedKeys) {
        if (collectedKeys > 0) showIntroDialog = false
    }

    val questSpots = remember {
        listOf(
            QuestSpot("q_light", "La Bussola Magica", "Trova il Nord per diradare la nebbia", QuestType.LIGHT, Offset(600f, 600f)),
            QuestSpot("q_gyro", "Il Sentiero Tortuoso", "Guida l'animale inclinando il telefono", QuestType.GYRO, Offset(1500f, 800f)),
            QuestSpot("q_accel", "L'Albero Antico", "Scuoti l'albero per far cadere la chiave", QuestType.CAMERA, Offset(1000f, 2000f))
        )
    }

    val testWeather by remember { mutableStateOf(currentWeather) }
    val testNight by remember { mutableStateOf(isNightTime) }
    val vividFilter = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.4f) }) }

    // HYDRATE INIZIALE
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

    // LOOP DI GIOCO
    LaunchedEffect(target) {
        while (true) {
            val dx = target.x - avatar.x
            val dy = target.y - avatar.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val speed = 12f

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

    // AUTOSAVE
    LaunchedEffect(sessionId, currentUid) {
        snapshotFlow {
            listOf(avatar.x, avatar.y, target.x, target.y, activeQuestId, completed.size)
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
                    .pointerInput(isGameWon) {
                        detectTapGestures { tap ->
                            val currentPan = panState.value
                            val worldTap = (tap - currentPan) / FIXED_ZOOM

                            AppAudio.playClick()

                            val hitQuest = questSpots.firstOrNull { spot ->
                                hypot((worldTap.x - spot.position.x).toDouble(), (worldTap.y - spot.position.y).toDouble()) <= spot.radiusPx
                            }

                            var hitCage: SceneryObject? = null
                            var hitBear: SceneryObject? = null

                            if (collectedKeys < 3) {
                                hitCage = staticScenery.firstOrNull {
                                    it.type == SceneryType.CAGE &&
                                            hypot((worldTap.x - it.x).toDouble(), (worldTap.y - it.y).toDouble()) <= 150f
                                }
                            } else {
                                hitBear = staticScenery.firstOrNull {
                                    it.type == SceneryType.NPC_PRISONER &&
                                            hypot((worldTap.x - it.x).toDouble(), (worldTap.y - it.y).toDouble()) <= 150f
                                }
                            }

                            if (hitQuest != null) {
                                selectedQuest = hitQuest
                            } else if (hitCage != null) {
                                showCageDialog = true
                            } else if (hitBear != null) {
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
                                val w = signBitmap.width.toFloat() * 3.5f
                                val h = signBitmap.height.toFloat() * 3.5f
                                scope.drawImage(image = signBitmap, dstOffset = IntOffset((quest.position.x - w/2).toInt(), (quest.position.y - h).toInt()), dstSize = IntSize(w.toInt(), h.toInt()))
                            }
                        })
                    }

                    // DISEGNO AVATAR
                    drawQueue.add(object : DrawableItem {
                        override val y = avatar.y + 20f
                        override fun draw(scope: DrawScope) {
                            val w = myAvatarBitmap.width.toFloat()
                            val h = myAvatarBitmap.height.toFloat()
                            val scale = AVATAR_SIZE_PX / h
                            val finalW = w * scale
                            val finalH = h * scale
                            val isGoingLeft = target.x < avatar.x

                            scope.withTransform({
                                translate(avatar.x, avatar.y - finalH + DRAW_OFFSET_Y)
                                if (isGoingLeft) {
                                    scale(-1f, 1f, pivot = Offset.Zero)
                                }
                            }) {
                                scope.drawImage(
                                    image = myAvatarBitmap,
                                    dstOffset = IntOffset((-finalW / 2).toInt(), 0),
                                    dstSize = IntSize(finalW.toInt(), finalH.toInt()),
                                    filterQuality = FilterQuality.None
                                )
                            }


                            val markerW = 20f
                            val markerH = 35f

                            val markerCenterX = avatar.x
                            val markerCenterYBase = avatar.y - finalH
                            val markerCenterY = markerCenterYBase + markerBobY - (markerH / 2) + 35f

                            val diamondPath = Path().apply {
                                moveTo(markerCenterX, markerCenterY - markerH / 2)
                                lineTo(markerCenterX + markerW / 2, markerCenterY)
                                lineTo(markerCenterX, markerCenterY + markerH / 2)
                                lineTo(markerCenterX - markerW / 2, markerCenterY)
                                close()
                            }

                            // 1. Riempimento
                            scope.drawPath(
                                path = diamondPath,
                                color = Color(0xFF00E676).copy(alpha = 0.9f),
                                style = Fill
                            )

                            // 2. Bordo
                            scope.drawPath(
                                path = diamondPath,
                                color = Color(0xFF006400),
                                style = Stroke(width = 2f)
                            )
                        }
                    })

                    // DISEGNO ALTRI GIOCATORI
                    session?.members?.forEach { m ->
                        if (m.uid != currentUid) {
                            val pos = uidToSpawn(m.uid, worldCenter)
                            val otherAvatar = allAvatars[m.avatar] ?: allAvatars["fox"]!!

                            drawQueue.add(object : DrawableItem {
                                override val y = pos.y + 20f
                                override fun draw(scope: DrawScope) {
                                    val w = otherAvatar.width.toFloat()
                                    val h = otherAvatar.height.toFloat()
                                    val scale = AVATAR_SIZE_PX / h
                                    val finalW = w * scale
                                    val finalH = h * scale

                                    // Gli altri giocatori erano già centrati con (pos.x - finalW/2)
                                    // Mantengo la logica coerente
                                    scope.drawImage(
                                        image = otherAvatar,
                                        dstOffset = IntOffset((pos.x - finalW/2).toInt(), (pos.y - finalH + DRAW_OFFSET_Y).toInt()),
                                        dstSize = IntSize(finalW.toInt(), finalH.toInt()),
                                        alpha = 0.7f,
                                        filterQuality = FilterQuality.None
                                    )
                                }
                            })
                        }
                    }

                    drawQueue.sortBy { it.y }
                    drawQueue.forEach { it.draw(this) }
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
            // TOP BAR
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeHUD(testNight)
                    if (DEBUG_MODE) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha=0.7f))) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Keys: $collectedKeys", color = Color.Green, fontSize = 12.sp)
                                Text("List: ${completed.joinToString()}", color = Color.Yellow, fontSize = 10.sp)
                            }
                        }
                    }
                }
                KeysHUD(collectedKeys)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                    WeatherHUD(testWeather)
                    Button(
                        onClick = { },
                        enabled = collectedKeys < 3,
                        modifier = Modifier.height(35.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("+ Key", fontSize = 12.sp) }
                }
            }

            // BOTTOM BAR
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                ForestButton(text = "Reset", onClick = { avatar = spawn; target = spawn }, modifier = Modifier.width(100.dp))
                ForestButton(text = "Menu", onClick = { (context as? android.app.Activity)?.onBackPressed() }, modifier = Modifier.width(100.dp))
                ForestButton(text = "Esci", onClick = onLeave, modifier = Modifier.width(100.dp))
            }
        }

        // --- GESTIONE VITTORIA MINIGIOCHI ---
        val handleWin = {
            val qId = activeQuestId

            if (qId != null) {
                if (completed.contains(qId)) {
                    showAlreadyCollectedDialog = true
                } else {
                    val newCompleted = completed + qId
                    completed = newCompleted

                    if (newCompleted.size >= 3) {
                        showWinDialog = true
                        AppAudio.playWin()
                    }
                }
            }
            activeMinigame = null
            activeQuestId = null
        }

        // --- OVERLAY MINIGIOCHI ---
        when (activeMinigame) {
            QuestType.CAMERA -> {
                MinigameShake(
                    onDismiss = { activeMinigame = null; activeQuestId = null },
                    onWin = { handleWin() }
                )
            }
            QuestType.GYRO -> {
                MinigameGyro(
                    onDismiss = { activeMinigame = null; activeQuestId = null },
                    onWin = { handleWin() },
                    avatarResId = R.drawable.av_fox
                )
            }
            QuestType.LIGHT -> {
                MinigameCompass(
                    onDismiss = { activeMinigame = null; activeQuestId = null },
                    onWin = { handleWin() }
                )
            }
            null -> { }
        }
    }

    // --- DIALOGHI ---
    if (showIntroDialog && collectedKeys == 0) {
        ForestDialog(
            title = "Benvenuto!",
            text = "Il tuo amico Orso è stato catturato!\n\nCompleta le 3 missioni dei Saggi per ottenere le Chiavi Magiche e liberarlo.",
            onDismiss = { showIntroDialog = false },
            confirmText = "Inizia!",
            confirmAction = { showIntroDialog = false; AppAudio.playClick() }
        )
    }

    val sq = selectedQuest
    if (sq != null) {
        ForestDialog(
            title = sq.title,
            text = sq.description,
            onDismiss = { selectedQuest = null },
            confirmText = "Gioca",
            confirmAction = {
                activeQuestId = sq.id
                activeMinigame = sq.type
                selectedQuest = null
                AppAudio.playClick()
            },
            dismissText = "Annulla",
            dismissAction = { selectedQuest = null }
        )
    }

    if (showWinDialog) {
        ForestDialog(
            title = "L'ORSO È LIBERO!",
            text = "Hai raccolto tutte le 3 chiavi!\nLa gabbia si è aperta e il tuo amico è salvo grazie a te.",
            onDismiss = { },
            confirmText = "Torna al Menu",
            confirmAction = onLeave,
            dismissText = "Resta qui",
            dismissAction = { showWinDialog = false }
        )
    }

    if (showCageDialog) {
        ForestDialog(
            title = "Gabbia Chiusa",
            text = "Ti servono 3 Chiavi per aprirla.\nNe hai raccolte ${collectedKeys}/3.",
            onDismiss = { showCageDialog = false },
            confirmText = "Ok",
            confirmAction = { showCageDialog = false }
        )
    }

    if (showBearDialog) {
        ForestDialog(
            title = "Grazie Amico!",
            text = "Mi hai salvato! Non dimenticherò mai il tuo aiuto.\nLa foresta è un posto migliore grazie a te.",
            onDismiss = { showBearDialog = false },
            confirmText = "Prego!",
            confirmAction = { showBearDialog = false }
        )
    }

    if (showAlreadyCollectedDialog) {
        ForestDialog(
            title = "Già Completata!",
            text = "Questa chiave è già stata raccolta da un altro giocatore!\nNon ne riceverai un'altra.",
            onDismiss = { showAlreadyCollectedDialog = false },
            confirmText = "Ok, capito",
            confirmAction = { showAlreadyCollectedDialog = false }
        )
    }
}

// ... Huds & Helpers Standard ...
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