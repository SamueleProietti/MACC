package it.sapienza.forestanimalsgame.ui.game

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
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.runtime.snapshotFlow

import kotlin.math.hypot
import androidx.compose.foundation.BorderStroke

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
    val radiusPx: Float = 55f
)

private data class GameStatePayload(
    val avatarX: Float,
    val avatarY: Float,
    val targetX: Float,
    val targetY: Float,
    val activeQuestId: String?,
    val completedSorted: List<String>,
    val panX: Float,
    val panY: Float,
    val zoom: Float
)

@Composable
fun GameScreen(
    sessionId: String,
    session: Session?,
    avatarId: String,
    initialGameState: GameState?,
    onAutoSave: (GameState) -> Unit,

    // ✅ STOP: torna alla pagina principale senza chiamare leaveSession
    onStop: (GameState) -> Unit,

    // ✅ ESCI: abbandono (leaveSession)
    onLeave: () -> Unit
) {
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "unknown" }

    val worldSize = remember { Size(width = 4000f, height = 4000f) }
    val worldCenter = remember(worldSize) { Offset(worldSize.width / 2f, worldSize.height / 2f) }
    val spawn = remember(currentUid, worldCenter) { uidToSpawn(currentUid, worldCenter) }

    val context = LocalContext.current
    val resId = remember(avatarId) { avatarResId(avatarId) }
    val avatarBitmap = remember(resId) { ImageBitmap.imageResource(context.resources, resId) }

    val avatarSizePx = 336f
    val avatarHitRadiusPx = 100f
    val half = avatarHitRadiusPx

    var canvasSize by remember { mutableStateOf(IntSize(0, 0)) }

    var zoom by rememberSaveable { mutableStateOf(1f) }
    var pan by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }

    val minZoom = 0.6f
    val maxZoom = 3.0f

    val scheme = MaterialTheme.colorScheme
    val bgColor = scheme.surfaceVariant
    val treeColor = scheme.primary.copy(alpha = 0.35f)
    val questNormal = scheme.tertiary
    val questActive = scheme.error
    val questDone = scheme.secondary

    var avatar by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(spawn) }
    var target by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(spawn) }

    var selectedQuest by remember { mutableStateOf<QuestSpot?>(null) }
    var activeQuestId by rememberSaveable { mutableStateOf<String?>(null) }
    var completed by rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(setOf<String>()) }

    val questSpots = remember {
        listOf(
            QuestSpot(
                id = "q_light",
                title = "Lanterna nella foresta",
                description = "Abbassa la luminosità sotto una soglia per alcuni secondi. (placeholder)",
                type = QuestType.LIGHT,
                position = Offset(200f, 250f)
            ),
            QuestSpot(
                id = "q_gyro",
                title = "Equilibrio del gufo",
                description = "Mantieni il telefono stabile per alcuni secondi. (placeholder)",
                type = QuestType.GYRO,
                position = Offset(550f, 350f)
            ),
            QuestSpot(
                id = "q_camera",
                title = "Prova fotografica",
                description = "Scatta una foto e verifica un criterio semplice. (placeholder)",
                type = QuestType.CAMERA,
                position = Offset(400f, 900f)
            )
        )
    }

    // ------------------- HYDRATE (Resume) -------------------
    var hydrated by remember(sessionId, currentUid) { mutableStateOf(false) }

    LaunchedEffect(sessionId, currentUid, initialGameState) {
        if (hydrated) return@LaunchedEffect
        val gs = initialGameState
        if (gs != null) {
            avatar = clampCenter(Offset(gs.avatarX.toFloat(), gs.avatarY.toFloat()), worldSize.width, worldSize.height, half)
            target = clampCenter(Offset(gs.targetX.toFloat(), gs.targetY.toFloat()), worldSize.width, worldSize.height, half)
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
            pan = clampPan(
                pan = desired,
                canvasW = canvasSize.width.toFloat(),
                canvasH = canvasSize.height.toFloat(),
                worldW = worldSize.width,
                worldH = worldSize.height,
                zoom = zoom
            )
            cameraInitialized = true
        }
    }

    // ------------------- MOVEMENT -------------------
    LaunchedEffect(target) {
        repeat(40) {
            val dx = target.x - avatar.x
            val dy = target.y - avatar.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dist < 1f) return@LaunchedEffect

            val step = 0.12f
            val next = Offset(
                x = avatar.x + dx * step,
                y = avatar.y + dy * step
            )
            avatar = clampCenter(next, worldSize.width, worldSize.height, half)
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

    // ------------------- AUTOSAVE (debounce) -------------------
    LaunchedEffect(sessionId, currentUid) {
        snapshotFlow {
            GameStatePayload(
                avatarX = avatar.x,
                avatarY = avatar.y,
                targetX = target.x,
                targetY = target.y,
                activeQuestId = activeQuestId,
                completedSorted = completed.toList().sorted(),
                panX = pan.x,
                panY = pan.y,
                zoom = zoom
            )
        }
            .distinctUntilChanged()
            .debounce(900)
            .collect { p ->
                onAutoSave(
                    GameState(
                        avatarX = p.avatarX.toDouble(),
                        avatarY = p.avatarY.toDouble(),
                        targetX = p.targetX.toDouble(),
                        targetY = p.targetY.toDouble(),
                        activeQuestId = p.activeQuestId,
                        completed = p.completedSorted,
                        panX = p.panX.toDouble(),
                        panY = p.panY.toDouble(),
                        zoom = p.zoom.toDouble(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
    }

    // ------------------- UI -------------------
    Column(modifier = Modifier.fillMaxSize()) {

        // HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Sessione", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(sessionId, style = MaterialTheme.typography.bodySmall)
                Text("Stato: ${session?.status ?: "?"}", style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // ✅ STOP: torna alla main, sessione resta (resume OK)
                OutlinedButton(
                    onClick = {
                        // ✅ stato attuale completo, incluso "completed"
                        val state = GameState(
                            avatarX = avatar.x.toDouble(),
                            avatarY = avatar.y.toDouble(),
                            targetX = target.x.toDouble(),
                            targetY = target.y.toDouble(),
                            activeQuestId = activeQuestId,
                            completed = completed.toList().sorted(),
                            panX = pan.x.toDouble(),
                            panY = pan.y.toDouble(),
                            zoom = zoom.toDouble(),
                            updatedAt = System.currentTimeMillis()
                        )
                        onStop(state) // LobbyActivity farà save+finish
                    }
                ) { Text("Stop") }


                // ✅ ESCI: abbandono
                Button(onClick = onLeave) { Text("Esci") }
            }
        }

        if (activeQuestId != null) {
            val spot = questSpots.firstOrNull { it.id == activeQuestId }
            if (spot != null) {
                Text(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    text = "Missione attiva: ${spot.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(14.dp)
        ) {
            val frameShape = RoundedCornerShape(18.dp)

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(frameShape)
                    .clipToBounds(),
                shape = frameShape,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, scheme.outlineVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
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
                                        clampPan(
                                            pan = tentativePan,
                                            canvasW = cw,
                                            canvasH = ch,
                                            worldW = worldSize.width,
                                            worldH = worldSize.height,
                                            zoom = zoom
                                        )
                                    } else {
                                        tentativePan
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { tap ->
                                    val worldTap = screenToWorld(tap, pan, zoom)

                                    val hit = questSpots.firstOrNull { spot ->
                                        val d = hypot(
                                            (worldTap.x - spot.position.x).toDouble(),
                                            (worldTap.y - spot.position.y).toDouble()
                                        ).toFloat()
                                        d <= spot.radiusPx
                                    }

                                    if (hit != null) {
                                        selectedQuest = hit
                                    } else {
                                        target = clampCenter(worldTap, worldSize.width, worldSize.height, half)
                                    }
                                }
                            }
                    ) {
                        canvasSize = IntSize(size.width.toInt(), size.height.toInt())

                        pan = clampPan(
                            pan = pan,
                            canvasW = size.width,
                            canvasH = size.height,
                            worldW = worldSize.width,
                            worldH = worldSize.height,
                            zoom = zoom
                        )

                        withTransform({
                            translate(pan.x, pan.y)
                            scale(zoom, zoom)
                        }) {
                            drawRect(color = bgColor, size = worldSize)

                            fun tree(x: Float, y: Float, r: Float) {
                                drawCircle(color = treeColor, radius = r, center = Offset(x, y))
                            }
                            tree(120f, 120f, 55f)
                            tree(820f, 210f, 65f)
                            tree(900f, 980f, 75f)
                            tree(150f, 980f, 60f)

                            questSpots.forEach { spot ->
                                val isDone = spot.id in completed
                                val isActive = spot.id == activeQuestId
                                val c = when {
                                    isDone -> questDone
                                    isActive -> questActive
                                    else -> questNormal
                                }
                                drawCircle(color = c, radius = spot.radiusPx, center = spot.position)
                            }

                            val members = session?.members.orEmpty()
                            members.forEach { m ->
                                val p = uidToSpawn(m.uid, worldCenter)
                                drawCircle(
                                    color = scheme.primary.copy(alpha = 0.55f),
                                    radius = 18f,
                                    center = p
                                )
                            }

                            val dstOffset = IntOffset(
                                (avatar.x - avatarSizePx / 2f).toInt(),
                                (avatar.y - avatarSizePx / 2f).toInt()
                            )
                            drawImage(
                                image = avatarBitmap,
                                dstOffset = dstOffset,
                                dstSize = IntSize(avatarSizePx.toInt(), avatarSizePx.toInt())
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = {
                    avatar = spawn
                    target = spawn
                    activeQuestId = null
                    completed = emptySet()

                    zoom = 1f
                    pan = if (canvasSize.width > 0 && canvasSize.height > 0) {
                        val desired = Offset(
                            x = canvasSize.width / 2f - avatar.x * zoom,
                            y = canvasSize.height / 2f - avatar.y * zoom
                        )
                        clampPan(
                            pan = desired,
                            canvasW = canvasSize.width.toFloat(),
                            canvasH = canvasSize.height.toFloat(),
                            worldW = worldSize.width,
                            worldH = worldSize.height,
                            zoom = zoom
                        )
                    } else Offset.Zero
                }
            ) { Text("Reset") }

            Button(
                onClick = {
                    val next = questSpots.firstOrNull { it.id !in completed }
                    if (next != null) {
                        activeQuestId = next.id
                        target = next.position
                    }
                },
                enabled = activeQuestId == null && completed.size < questSpots.size
            ) { Text("Nuova missione") }
        }
    }

    val sq = selectedQuest
    if (sq != null) {
        val done = sq.id in completed
        val active = sq.id == activeQuestId
        AlertDialog(
            onDismissRequest = { selectedQuest = null },
            title = { Text(sq.title) },
            text = {
                Text(
                    when {
                        done -> "Completata ✅"
                        active -> "È la missione attiva. Vai sul punto evidenziato!"
                        else -> sq.description
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeQuestId = sq.id
                        target = sq.position
                        selectedQuest = null
                    },
                    enabled = !done
                ) { Text(if (done) "Ok" else "Attiva") }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedQuest = null }) { Text("Chiudi") }
            }
        )
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

private fun clampCenter(p: Offset, width: Float, height: Float, half: Float): Offset {
    val x = p.x.coerceIn(half, width - half)
    val y = p.y.coerceIn(half, height - half)
    return Offset(x, y)
}

private fun clampPan(
    pan: Offset,
    canvasW: Float,
    canvasH: Float,
    worldW: Float,
    worldH: Float,
    zoom: Float
): Offset {
    val worldScaledW = worldW * zoom
    val worldScaledH = worldH * zoom

    val (minX, maxX) = if (worldScaledW <= canvasW) {
        val c = (canvasW - worldScaledW) / 2f
        c to c
    } else {
        (canvasW - worldScaledW) to 0f
    }

    val (minY, maxY) = if (worldScaledH <= canvasH) {
        val c = (canvasH - worldScaledH) / 2f
        c to c
    } else {
        (canvasH - worldScaledH) to 0f
    }

    return Offset(
        x = pan.x.coerceIn(minX, maxX),
        y = pan.y.coerceIn(minY, maxY)
    )
}

private fun screenToWorld(screen: Offset, pan: Offset, zoom: Float): Offset =
    (screen - pan) / zoom
