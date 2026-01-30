package it.sapienza.forestanimalsgame.ui.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
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

// --- SAVERS ---
private val OffsetSaver: Saver<Offset, ArrayList<Float>> = Saver(
    save = { arrayListOf(it.x, it.y) },
    restore = { Offset(it[0], it[1]) }
)

private val StringSetSaver: Saver<Set<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it) },
    restore = { it.toSet() }
)

// --- MODELLI DI GIOCO ---
private enum class QuestType { LIGHT, GYRO, CAMERA }

private data class QuestSpot(
    val id: String,
    val title: String,
    val description: String,
    val type: QuestType,
    val position: Offset,
    val radiusPx: Float = 60f
)

// Wrapper per ordinare graficamente gli oggetti (2.5D Sorting)
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
    onAutoSave: (GameState) -> Unit,
    onStop: (GameState) -> Unit,
    onLeave: () -> Unit
) {
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "unknown" }

    // 1. SETUP MONDO
    val worldSize = remember { Size(width = 4000f, height = 4000f) }
    val worldCenter = remember(worldSize) { Offset(worldSize.width / 2f, worldSize.height / 2f) }
    val spawn = remember(currentUid, worldCenter) { uidToSpawn(currentUid, worldCenter) }

    // 2. CARICAMENTO RISORSE GRAFICHE (ForestMap.kt + Locali)
    val context = LocalContext.current
    val resId = remember(avatarId) { avatarResId(avatarId) }
    val avatarBitmap = remember(resId) { ImageBitmap.imageResource(context.resources, resId) }
    
    // Risorse della mappa (Alberi, Rocce, Terreno...)
    val terrainTile = rememberTerrainTile()
    val forestResources = rememberForestResources()
    
    // Risorse extra
    val cloudBitmap = remember { ImageBitmap.imageResource(context.resources, R.drawable.clouds_512x512) }
    val signBitmap = forestResources[SceneryType.SIGN]!! // Cartello per le quest

    // Generazione Scenery (Alberi/Rocce) - Una volta sola!
    val staticScenery = remember(worldSize) { generateForestScenery(worldSize, 250) }

    // 3. STATO DELLA CAMERA E DEL GIOCO
    val avatarSizePx = 120f // Dimensione visiva avatar
    val avatarHitRadiusPx = 60f
    
    var canvasSize by remember { mutableStateOf(IntSize(0, 0)) }
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var pan by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }

    val minZoom = 0.5f
    val maxZoom = 2.5f

    var avatar by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(spawn) }
    var target by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(spawn) }

    var selectedQuest by remember { mutableStateOf<QuestSpot?>(null) }
    var activeQuestId by rememberSaveable { mutableStateOf<String?>(null) }
    var completed by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(setOf<String>()) }

    // Animazione Nuvole
    val infiniteTransition = rememberInfiniteTransition(label = "clouds")
    val cloudOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing), // 60 secondi per loop
            repeatMode = RepeatMode.Restart
        ),
        label = "cloudMove"
    )

    // Lista Missioni (Posizioni fisse sulla mappa)
    val questSpots = remember {
        listOf(
            QuestSpot("q_light", "La Radura Oscura", "Trova la radura (placeholder)", QuestType.LIGHT, Offset(600f, 600f)),
            QuestSpot("q_gyro", "L'Albero Caduto", "Mantieni l'equilibrio (placeholder)", QuestType.GYRO, Offset(1500f, 800f)),
            QuestSpot("q_camera", "Il Grande Fungo", "Scatta una foto (placeholder)", QuestType.CAMERA, Offset(1000f, 2000f))
        )
    }

    // ------------------- HYDRATE (Resume) -------------------
    var hydrated by remember(sessionId, currentUid) { mutableStateOf(false) }
    LaunchedEffect(sessionId, currentUid, initialGameState) {
        if (hydrated) return@LaunchedEffect
        val gs = initialGameState
        if (gs != null) {
            avatar = clampCenter(Offset(gs.avatarX.toFloat(), gs.avatarY.toFloat()), worldSize.width, worldSize.height, avatarHitRadiusPx)
            target = clampCenter(Offset(gs.targetX.toFloat(), gs.targetY.toFloat()), worldSize.width, worldSize.height, avatarHitRadiusPx)
            activeQuestId = gs.activeQuestId
            completed = gs.completed.toSet()
            zoom = gs.zoom.toFloat().coerceIn(minZoom, maxZoom)
            pan = Offset(gs.panX.toFloat(), gs.panY.toFloat())
        }
        hydrated = true
    }

    // ------------------- CAMERA INIT -------------------
    var cameraInitialized by remember(sessionId) { mutableStateOf(false) }
    LaunchedEffect(canvasSize, sessionId) {
        if (!cameraInitialized && canvasSize.width > 0 && canvasSize.height > 0) {
            val desired = Offset(
                x = canvasSize.width / 2f - avatar.x * zoom,
                y = canvasSize.height / 2f - avatar.y * zoom
            )
            pan = clampPan(desired, canvasSize.width.toFloat(), canvasSize.height.toFloat(), worldSize.width, worldSize.height, zoom)
            cameraInitialized = true
        }
    }

    // ------------------- MOVEMENT LOOP -------------------
    LaunchedEffect(target) {
        // Movimento fluido verso il target
        while (true) {
            val dx = target.x - avatar.x
            val dy = target.y - avatar.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dist < 2f) break

            val speed = 8f // Velocità pixel/frame
            val stepX = (dx / dist) * speed
            val stepY = (dy / dist) * speed
            
            val next = Offset(avatar.x + stepX, avatar.y + stepY)
            avatar = clampCenter(next, worldSize.width, worldSize.height, avatarHitRadiusPx)
            delay(16) // ~60fps
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

    // ------------------- AUTOSAVE -------------------
    LaunchedEffect(sessionId, currentUid) {
        snapshotFlow {
            // Creo un oggetto semplice per monitorare i cambiamenti
            listOf(avatar.x, avatar.y, target.x, target.y, zoom, pan.x, pan.y, activeQuestId, completed.size)
        }
            .distinctUntilChanged()
            .debounce(1000) // Salva ogni secondo se ci sono modifiche
            .collect {
                onAutoSave(
                    GameState(
                        avatarX = avatar.x.toDouble(), avatarY = avatar.y.toDouble(),
                        targetX = target.x.toDouble(), targetY = target.y.toDouble(),
                        activeQuestId = activeQuestId, completed = completed.toList().sorted(),
                        panX = pan.x.toDouble(), panY = pan.y.toDouble(), zoom = zoom.toDouble(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
    }

    // ------------------- UI LAYOUT -------------------
    Column(modifier = Modifier.fillMaxSize()) {

        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Foresta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Status: ${session?.status ?: "..."}", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val state = GameState(
                        avatarX = avatar.x.toDouble(), avatarY = avatar.y.toDouble(),
                        targetX = target.x.toDouble(), targetY = target.y.toDouble(),
                        activeQuestId = activeQuestId, completed = completed.toList().sorted(),
                        panX = pan.x.toDouble(), panY = pan.y.toDouble(), zoom = zoom.toDouble(),
                        updatedAt = System.currentTimeMillis()
                    )
                    onStop(state)
                }) { Text("Menu") }
                Button(onClick = onLeave, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Esci") }
            }
        }

        if (activeQuestId != null) {
            val spot = questSpots.firstOrNull { it.id == activeQuestId }
            if (spot != null) {
                Card(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "Obiettivo: ${spot.title}",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // --- GAME VIEWPORT ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(14.dp)
        ) {
            val frameShape = RoundedCornerShape(16.dp)
            Surface(
                modifier = Modifier.fillMaxSize().clip(frameShape).clipToBounds(),
                shape = frameShape,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant),
                color = Color(0xFF388E3C) // Colore base erba se il tiling fallisce
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, panChange, zoomChange, _ ->
                                val oldZoom = zoom
                                val newZoom = (zoom * zoomChange).coerceIn(minZoom, maxZoom)
                                val zoomFactor = newZoom / oldZoom
                                val tentativePan = pan + panChange + (centroid - pan) * (1 - zoomFactor)
                                zoom = newZoom
                                val cw = canvasSize.width.toFloat()
                                val ch = canvasSize.height.toFloat()
                                pan = if (cw > 0f && ch > 0f) {
                                    clampPan(tentativePan, cw, ch, worldSize.width, worldSize.height, zoom)
                                } else tentativePan
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { tap ->
                                val worldTap = (tap - pan) / zoom
                                
                                // Cliccato su una quest?
                                val hitQuest = questSpots.firstOrNull { spot ->
                                    hypot((worldTap.x - spot.position.x).toDouble(), (worldTap.y - spot.position.y).toDouble()) <= spot.radiusPx
                                }

                                if (hitQuest != null) {
                                    selectedQuest = hitQuest
                                } else {
                                    // Muovi avatar
                                    target = clampCenter(worldTap, worldSize.width, worldSize.height, avatarHitRadiusPx)
                                }
                            }
                        }
                ) {
                    canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                    
                    // Limita pan per non uscire dal mondo
                    pan = clampPan(pan, size.width, size.height, worldSize.width, worldSize.height, zoom)

                    withTransform({
                        translate(pan.x, pan.y)
                        scale(zoom, zoom)
                    }) {
                        // --- 1. DISEGNO TERRENO (Tiling) ---
                        // Disegniamo l'erba ripetuta
                        val tileSize = 32f // Dimensione px della texture prato.png
                        val cols = (worldSize.width / tileSize).toInt()
                        val rows = (worldSize.height / tileSize).toInt()
                        
                        // Ottimizzazione: Disegna solo le tile visibili (Culling)
                        val viewLeft = -pan.x / zoom
                        val viewTop = -pan.y / zoom
                        val viewRight = viewLeft + size.width / zoom
                        val viewBottom = viewTop + size.height / zoom
                        
                        val startCol = (viewLeft / tileSize).toInt().coerceIn(0, cols)
                        val endCol = (viewRight / tileSize).toInt().coerceIn(0, cols)
                        val startRow = (viewTop / tileSize).toInt().coerceIn(0, rows)
                        val endRow = (viewBottom / tileSize).toInt().coerceIn(0, rows)

                        for (r in startRow..endRow) {
                            for (c in startCol..endCol) {
                                drawImage(
                                    image = terrainTile,
                                    dstOffset = IntOffset((c * tileSize).toInt(), (r * tileSize).toInt())
                                )
                            }
                        }

                        // --- 2. COSTRUZIONE CODA DI DISEGNO (2.5D Sorting) ---
                        val drawQueue = ArrayList<DrawableItem>()

                        // A. Elementi statici (Alberi, Rocce)
                        staticScenery.forEach { obj ->
                            // Culling semplice
                            if (obj.x in (viewLeft - 200)..(viewRight + 200) && 
                                obj.y in (viewTop - 200)..(viewBottom + 200)) {
                                
                                val bmp = forestResources[obj.type]
                                if (bmp != null) {
                                    drawQueue.add(object : DrawableItem {
                                        override val y = obj.y
                                        override fun draw(scope: DrawScope) {
                                            val w = bmp.width * obj.scale
                                            val h = bmp.height * obj.scale
                                            scope.drawImage(
                                                image = bmp,
                                                dstOffset = IntOffset((obj.x - w/2).toInt(), (obj.y - h).toInt()), // Ancora in basso al centro
                                                dstSize = IntSize(w.toInt(), h.toInt())
                                            )
                                        }
                                    })
                                }
                            }
                        }

                        // B. Cartelli Missioni
                        questSpots.forEach { quest ->
                            drawQueue.add(object : DrawableItem {
                                override val y = quest.position.y
                                override fun draw(scope: DrawScope) {
                                    val isCompleted = quest.id in completed
                                    val alpha = if (isCompleted) 0.5f else 1f
                                    val w = signBitmap.width.toFloat()
                                    val h = signBitmap.height.toFloat()
                                    scope.drawImage(
                                        image = signBitmap,
                                        dstOffset = IntOffset((quest.position.x - w/2).toInt(), (quest.position.y - h).toInt()),
                                        alpha = alpha
                                    )
                                }
                            })
                        }

                        // C. Avatar Giocatore
                        drawQueue.add(object : DrawableItem {
                            override val y = avatar.y
                            override fun draw(scope: DrawScope) {
                                scope.drawImage(
                                    image = avatarBitmap,
                                    dstOffset = IntOffset((avatar.x - avatarSizePx/2).toInt(), (avatar.y - avatarSizePx + 20).toInt()), // +20 offset piedi
                                    dstSize = IntSize(avatarSizePx.toInt(), avatarSizePx.toInt())
                                )
                            }
                        })

                        // D. Altri giocatori (Sessione)
                        session?.members?.forEach { m ->
                            if (m.uid != currentUid) {
                                val pos = uidToSpawn(m.uid, worldCenter) // Placeholder posizione
                                drawQueue.add(object : DrawableItem {
                                    override val y = pos.y
                                    override fun draw(scope: DrawScope) {
                                        // Usiamo lo stesso avatar o uno generico per gli altri
                                        scope.drawImage(
                                            image = avatarBitmap,
                                            dstOffset = IntOffset((pos.x - avatarSizePx/2).toInt(), (pos.y - avatarSizePx + 20).toInt()),
                                            dstSize = IntSize(avatarSizePx.toInt(), avatarSizePx.toInt()),
                                            alpha = 0.6f // Semitrasparenti
                                        )
                                    }
                                })
                            }
                        }

                        // --- 3. ORDINAMENTO E DISEGNO ---
                        drawQueue.sortBy { it.y }
                        drawQueue.forEach { it.draw(this) }
                    }
                    
                    // --- 4. OVERLAY METEO (Nuvole) ---
                    // Disegnate SOPRA tutto (fuori dal withTransform zoom/pan se vuoi che scorrano sullo schermo, 
                    // o dentro se vuoi che siano parte del mondo. Qui le metto come "parallasse" sopra la view)
                    withTransform({
                        // Movimento lento nuvole
                        translate(cloudOffset % 2000f, 0f) 
                    }) {
                        // Disegniamo nuvole ripetute per coprire lo schermo
                        val cloudW = cloudBitmap.width.toFloat() * 3
                        val cloudH = cloudBitmap.height.toFloat() * 3
                        drawImage(cloudBitmap, dstSize = IntSize(cloudW.toInt(), cloudH.toInt()), alpha = 0.2f)
                        drawImage(cloudBitmap, dstOffset = IntOffset(cloudW.toInt(), 0), dstSize = IntSize(cloudW.toInt(), cloudH.toInt()), alpha = 0.2f)
                    }
                }
            }
        }

        // CONTROLLI IN BASSO
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = {
                // Reset Camera e Posizione
                avatar = spawn
                target = spawn
                zoom = 1f
                pan = Offset.Zero
            }) { Text("Reset") }

            Button(onClick = {
                // Attiva prossima quest
                val next = questSpots.firstOrNull { it.id !in completed }
                if (next != null) {
                    activeQuestId = next.id
                    target = next.position
                }
            }, enabled = activeQuestId == null && completed.size < questSpots.size) {
                Text("Nuova Missione")
            }
        }
    }

    // DIALOG MISSIONE
    val sq = selectedQuest
    if (sq != null) {
        val done = sq.id in completed
        val active = sq.id == activeQuestId
        AlertDialog(
            onDismissRequest = { selectedQuest = null },
            title = { Text(sq.title) },
            text = {
                Column {
                    Text(sq.description)
                    if (done) Text("✅ Completata", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top=8.dp))
                    else if (active) Text("📍 Missione Attiva", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top=8.dp))
                }
            },
            confirmButton = {
                if (!done && !active) {
                    Button(onClick = {
                        activeQuestId = sq.id
                        target = sq.position
                        selectedQuest = null
                    }) { Text("Attiva") }
                }
            },
            dismissButton = { TextButton(onClick = { selectedQuest = null }) { Text("Chiudi") } }
        )
    }
}

// Helpers
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

private fun clampCenter(p: Offset, width: Float, height: Float, half: Float): Offset {
    return Offset(p.x.coerceIn(half, width - half), p.y.coerceIn(half, height - half))
}

private fun clampPan(pan: Offset, canvasW: Float, canvasH: Float, worldW: Float, worldH: Float, zoom: Float): Offset {
    val worldScaledW = worldW * zoom
    val worldScaledH = worldH * zoom
    val minX = if (worldScaledW <= canvasW) (canvasW - worldScaledW) / 2f else canvasW - worldScaledW
    val maxX = if (worldScaledW <= canvasW) (canvasW - worldScaledW) / 2f else 0f
    val minY = if (worldScaledH <= canvasH) (canvasH - worldScaledH) / 2f else canvasH - worldScaledH
    val maxY = if (worldScaledH <= canvasH) (canvasH - worldScaledH) / 2f else 0f
    return Offset(pan.x.coerceIn(minX, maxX), pan.y.coerceIn(minY, maxY))
}