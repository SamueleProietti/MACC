package it.sapienza.forestanimalsgame.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.forestanimalsgame.data.model.Member
import it.sapienza.forestanimalsgame.data.model.Session
import kotlinx.coroutines.delay
import kotlin.math.hypot

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
    onLeave: () -> Unit
) {
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "unknown" }
    val spawn = remember(currentUid) { uidToSpawn(currentUid) }

    // ---- IMPORTANT: prendo i colori qui (in contesto @Composable), NON dentro Canvas ----
    val scheme = MaterialTheme.colorScheme
    val bgColor = scheme.surfaceVariant
    val treeColor = scheme.primary.copy(alpha = 0.35f)
    val questNormal = scheme.tertiary
    val questActive = scheme.error
    val questDone = scheme.secondary

    // movimento (placeholder locale)
    var avatar by rememberSaveable { mutableStateOf(spawn) }
    var target by rememberSaveable { mutableStateOf(spawn) }

    // UI quest state (locale, per ora)
    var selectedQuest by remember { mutableStateOf<QuestSpot?>(null) }
    var activeQuestId by rememberSaveable { mutableStateOf<String?>(null) }
    var completed by rememberSaveable { mutableStateOf(setOf<String>()) }

    val questSpots = remember {
        listOf(
            QuestSpot(
                id = "q_light",
                title = "Lanterna nella foresta",
                description = "Abbassa la luminosità sotto una soglia per alcuni secondi.",
                type = QuestType.LIGHT,
                position = Offset(200f, 250f)
            ),
            QuestSpot(
                id = "q_gyro",
                title = "Equilibrio del gufo",
                description = "Mantieni il telefono stabile/in equilibrio per alcuni secondi.",
                type = QuestType.GYRO,
                position = Offset(550f, 350f)
            ),
            QuestSpot(
                id = "q_camera",
                title = "Prova fotografica",
                description = "Scatta una foto e verifica un criterio semplice (placeholder).",
                type = QuestType.CAMERA,
                position = Offset(400f, 900f)
            )
        )
    }

    // animazione verso target
    LaunchedEffect(target) {
        repeat(40) {
            val dx = target.x - avatar.x
            val dy = target.y - avatar.y
            val dist = hypot(dx, dy)
            if (dist < 2f) return@repeat
            avatar = Offset(
                x = avatar.x + dx * 0.15f,
                y = avatar.y + dy * 0.15f
            )
            delay(16L)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar (NO tonalElevation, usa shadowElevation o niente)
        Surface(shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Forest Map", fontWeight = FontWeight.Bold)
                    Text("Sessione: $sessionId", style = MaterialTheme.typography.bodySmall)
                    Text("Membri: ${(session?.members?.size ?: 0)}/4", style = MaterialTheme.typography.bodySmall)

                    val aq = questSpots.firstOrNull { it.id == activeQuestId }
                    Text(
                        text = if (aq != null) "Quest attiva: ${aq.title}" else "Quest attiva: -",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(onClick = onLeave) { Text("Esci") }
            }
        }

        // Canvas “2D world”
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            // 1) se tocchi uno spot -> apri dialog quest
                            val hit = questSpots.firstOrNull { spot ->
                                distance(tap, spot.position) <= spot.radiusPx
                            }
                            if (hit != null) {
                                selectedQuest = hit
                            } else {
                                // 2) altrimenti tap-to-move
                                target = tap
                            }
                        }
                    }
            ) {
                // background
                drawRect(
                    color = bgColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, size.height)
                )

                // alberi placeholder
                val trees = listOf(
                    Offset(100f, 120f),
                    Offset(650f, 150f),
                    Offset(80f, 700f),
                    Offset(700f, 800f),
                    Offset(300f, 500f)
                )
                trees.forEach {
                    drawCircle(
                        color = treeColor,
                        radius = 55f,
                        center = it
                    )
                }

                // quest spots (cambio stile se completata/attiva)
                questSpots.forEach { spot ->
                    val isCompleted = completed.contains(spot.id)
                    val isActive = (activeQuestId == spot.id)

                    val base = when {
                        isCompleted -> questDone
                        isActive -> questActive
                        else -> questNormal
                    }

                    drawCircle(
                        color = base.copy(alpha = 0.18f),
                        radius = spot.radiusPx,
                        center = spot.position
                    )
                    drawCircle(
                        color = base.copy(alpha = 0.55f),
                        radius = 38f,
                        center = spot.position
                    )
                    drawCircle(
                        color = base,
                        radius = 14f,
                        center = spot.position
                    )
                }

                // altri giocatori (placeholder: fermi in spawn deterministico)
                val members: List<Member> = session?.members ?: emptyList()
                members
                    .filter { it.uid.isNotBlank() && it.uid != currentUid }
                    .forEach { m ->
                        val p = uidToSpawn(m.uid)
                        val c = uidToColor(m.uid)
                        drawAvatar(center = p, color = c, isLocal = false)
                    }

                // avatar locale
                drawAvatar(center = avatar, color = uidToColor(currentUid), isLocal = true)
            }

            // Hint UI (NO tonalElevation: usa CardDefaults.cardElevation)
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("UI Quest", fontWeight = FontWeight.Bold)
                    Text(
                        "Tocca uno spot per aprire la quest. Tocca altrove per muoverti.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    // --- Quest Dialog (UI only) ---
    selectedQuest?.let { q ->
        QuestDialog(
            quest = q,
            isActive = (activeQuestId == q.id),
            isCompleted = completed.contains(q.id),
            onDismiss = { selectedQuest = null },
            onStart = {
                activeQuestId = q.id
                selectedQuest = null
            },
            onMarkCompleted = {
                completed = completed + q.id
                if (activeQuestId == q.id) activeQuestId = null
                selectedQuest = null
            }
        )
    }
}

@Composable
private fun QuestDialog(
    quest: QuestSpot,
    isActive: Boolean,
    isCompleted: Boolean,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onMarkCompleted: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(quest.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(quest.description)
                Text(
                    text = "Tipo: ${quest.type}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = when {
                        isCompleted -> "Stato: COMPLETATA"
                        isActive -> "Stato: IN CORSO"
                        else -> "Stato: NON AVVIATA"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Nota: è solo UI placeholder. Dopo colleghiamo sensori/Firestore.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStart,
                    enabled = !isCompleted && !isActive
                ) { Text("Start quest") }

                OutlinedButton(
                    onClick = onMarkCompleted,
                    enabled = !isCompleted
                ) { Text("Segna completata") }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Chiudi") }
        }
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAvatar(
    center: Offset,
    color: Color,
    isLocal: Boolean
) {
    drawCircle(
        color = color.copy(alpha = if (isLocal) 0.25f else 0.18f),
        radius = if (isLocal) 48f else 42f,
        center = center
    )
    drawCircle(
        color = color,
        radius = if (isLocal) 22f else 20f,
        center = center
    )
}

private fun uidToColor(uid: String): Color {
    val h = (uid.hashCode().toLong() and 0xFFFFFFFFL)
    val hue = (h % 360).toFloat()
    return Color.hsv(hue, 0.75f, 0.95f)
}

private fun uidToSpawn(uid: String): Offset {
    val spawns = listOf(
        Offset(180f, 650f),
        Offset(520f, 650f),
        Offset(180f, 950f),
        Offset(520f, 950f)
    )
    val idx = ((uid.hashCode().toLong() and 0xFFFFFFFFL) % spawns.size).toInt()
    return spawns[idx]
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return hypot(dx, dy)
}
