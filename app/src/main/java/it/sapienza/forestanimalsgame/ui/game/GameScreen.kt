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
import it.sapienza.forestanimalsgame.data.model.Session
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.abs
import androidx.compose.foundation.BorderStroke

// ------------------- SAVERS (Offset/Set) -------------------
private val OffsetSaver: Saver<Offset, ArrayList<Float>> = Saver(
    save = { arrayListOf(it.x, it.y) },
    restore = { Offset(it[0], it[1]) }
)

private val StringSetSaver: Saver<Set<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it) },
    restore = { it.toSet() }
)

// --- UI Quest models (placeholder) ---
private enum class QuestType { LIGHT, GYRO, CAMERA }

private data class QuestSpot(
    val id: String,
    val title: String,
    val description: String,
    val type: QuestType,
    val position: Offset,
    val radiusPx: Float = 55f
)

@Composable
fun GameScreen(
    sessionId: String,
    session: Session?,
    avatarId: String,
    onLeave: () -> Unit
) {
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "unknown" }

    // ------------------- WORLD MAP (più grande dello schermo) -------------------
    val worldSize = remember { Size(width = 4000f, height = 4000f) }
    val worldCenter = remember(worldSize) { Offset(worldSize.width / 2f, worldSize.height / 2f) }

    // Spawn AL CENTRO (con piccola variazione deterministica per UID)
    val spawn = remember(currentUid, worldCenter) { uidToSpawn(currentUid, worldCenter) }

    // --- Avatar bitmap ---
    val context = LocalContext.current
    val resId = remember(avatarId) { avatarResId(avatarId) }
    val avatarBitmap = remember(resId) { ImageBitmap.imageResource(context.resources, resId) }

    // Sprite size (in world px)
    val avatarSizePx = 336f

    // Clamp usando una HITBOX (riduce il “padding” eccessivo)
    val avatarHitRadiusPx = 100f
    val half = avatarHitRadiusPx

    // Canvas size (serve per clamp camera)
    var canvasSize by remember { mutableStateOf(IntSize(0, 0)) }

    // ------------------- CAMERA (pan + zoom) -------------------
    var zoom by rememberSaveable { mutableStateOf(1f) }
    var pan by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(Offset.Zero) }

    val minZoom = 0.6f
    val maxZoom = 3.0f

    // colori dal tema (NON dentro Canvas)
    val scheme = MaterialTheme.colorScheme
    val bgColor = scheme.surfaceVariant
    val treeColor = scheme.primary.copy(alpha = 0.35f)
    val questNormal = scheme.tertiary
    val questActive = scheme.error
    val questDone = scheme.secondary

    // Avatar e target in WORLD coords
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

    // inizializza camera centrando l'avatar (che ora parte al centro del mondo)
    var cameraInitialized by remember { mutableStateOf(false) }
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

    // movimento verso target (WORLD)
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

    // completa quest quando arrivi vicino (WORLD)
    LaunchedEffect(avatar, activeQuestId) {
        val qid = activeQuestId ?: return@LaunchedEffect
        val spot = questSpots.firstOrNull { it.id == qid } ?: return@LaunchedEffect
        val d = hypot((avatar.x - spot.position.x).toDouble(), (avatar.y - spot.position.y).toDouble()).toFloat()
        if (d <= spot.radiusPx) {
            completed = completed + qid
            activeQuestId = null
        }
    }

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
            OutlinedButton(onClick = onLeave) { Text("Esci") }
        }

        // INFO QUEST ATTIVA
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

        // MAPPA (cornice + clipping)
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
                // clipToBounds qui è fondamentale: niente disegno esce dalla cornice
                Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            // PINCH + PAN (2 dita)
                            .pointerInput(Unit) {
                                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                                    val oldZoom = zoom
                                    val newZoom = (zoom * zoomChange).coerceIn(minZoom, maxZoom)
                                    val zoomFactor = newZoom / oldZoom

                                    // zoom attorno al punto "centroid"
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
                            // TAP (1 dito / click mouse)
                            .pointerInput(Unit) {
                                detectTapGestures { tap ->
                                    val worldTap = screenToWorld(tap, pan, zoom)

                                    // tap su quest?
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
                        // aggiorna size
                        canvasSize = IntSize(size.width.toInt(), size.height.toInt())

                        // clamp pan sempre valido (rispetto alla CORNICE, cioè il Canvas)
                        pan = clampPan(
                            pan = pan,
                            canvasW = size.width,
                            canvasH = size.height,
                            worldW = worldSize.width,
                            worldH = worldSize.height,
                            zoom = zoom
                        )

                        // Applica camera: screen = world * zoom + pan
                        withTransform({
                            translate(pan.x, pan.y)
                            scale(zoom, zoom)
                        }) {
                            // sfondo WORLD
                            drawRect(color = bgColor, size = worldSize)

                            // alberi placeholder
                            fun tree(x: Float, y: Float, r: Float) {
                                drawCircle(color = treeColor, radius = r, center = Offset(x, y))
                            }
                            tree(120f, 120f, 55f)
                            tree(820f, 210f, 65f)
                            tree(900f, 980f, 75f)
                            tree(150f, 980f, 60f)

                            // quest spots
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

                            // altri player (placeholder) -> anche loro distribuiti attorno al centro
                            val members = session?.members.orEmpty()
                            members.forEach { m ->
                                val p = uidToSpawn(m.uid, worldCenter)
                                drawCircle(
                                    color = scheme.primary.copy(alpha = 0.55f),
                                    radius = 18f,
                                    center = p
                                )
                            }

                            // avatar (png), centrato su avatar (WORLD)
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

        // FOOTER
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

                    // reset camera (centra avatar)
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
            ) {
                Text("Nuova missione")
            }
        }
    }

    // DIALOG quest selezionata
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
                ) {
                    Text(if (done) "Ok" else "Attiva")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedQuest = null }) { Text("Chiudi") }
            }
        )
    }
}

// spawn attorno al centro (deterministico per uid)
private fun uidToSpawn(uid: String, center: Offset): Offset {
    val h = uid.hashCode()
    // offset massimo ~200px dal centro
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

// clamp del CENTRO dentro la WORLD map usando half = hitbox
private fun clampCenter(p: Offset, width: Float, height: Float, half: Float): Offset {
    val x = p.x.coerceIn(half, width - half)
    val y = p.y.coerceIn(half, height - half)
    return Offset(x, y)
}

// ------------------- CAMERA HELPERS -------------------

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

    val minX: Float
    val maxX: Float
    if (worldScaledW <= canvasW) {
        minX = (canvasW - worldScaledW) / 2f
        maxX = minX
    } else {
        minX = canvasW - worldScaledW
        maxX = 0f
    }

    val minY: Float
    val maxY: Float
    if (worldScaledH <= canvasH) {
        minY = (canvasH - worldScaledH) / 2f
        maxY = minY
    } else {
        minY = canvasH - worldScaledH
        maxY = 0f
    }

    return Offset(
        x = pan.x.coerceIn(minX, maxX),
        y = pan.y.coerceIn(minY, maxY)
    )
}

private fun screenToWorld(screen: Offset, pan: Offset, zoom: Float): Offset =
    (screen - pan) / zoom
