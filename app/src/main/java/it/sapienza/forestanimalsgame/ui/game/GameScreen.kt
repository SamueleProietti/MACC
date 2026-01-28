package it.sapienza.forestanimalsgame.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.forestanimalsgame.data.model.Session
import kotlinx.coroutines.delay
import kotlin.math.hypot

// ------------------- SAVERS (fix crash rememberSaveable + Offset/Set) -------------------
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
    onLeave: () -> Unit
) {
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "unknown" }
    val spawn = remember(currentUid) { uidToSpawn(currentUid) }

    // colori dal tema (NON dentro Canvas)
    val scheme = MaterialTheme.colorScheme
    val bgColor = scheme.surfaceVariant
    val treeColor = scheme.primary.copy(alpha = 0.35f)
    val questNormal = scheme.tertiary
    val questActive = scheme.error
    val questDone = scheme.secondary

    // ✅ Offset è saveable SOLO con Saver
    var avatar by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(spawn) }
    var target by rememberSaveable(stateSaver = OffsetSaver) { mutableStateOf(spawn) }

    var selectedQuest by remember { mutableStateOf<QuestSpot?>(null) }
    var activeQuestId by rememberSaveable { mutableStateOf<String?>(null) }

    // ✅ Set<String> saveable SOLO con Saver
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

    // movimento verso target
    LaunchedEffect(target) {
        repeat(40) {
            val dx = target.x - avatar.x
            val dy = target.y - avatar.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dist < 1f) return@LaunchedEffect

            val step = 0.12f
            avatar = Offset(
                x = avatar.x + dx * step,
                y = avatar.y + dy * step
            )
            delay(16)
        }
    }

    // completamento quest quando arrivi vicino
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

        // MAPPA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(14.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            // 1) tap su quest -> seleziona
                            val hit = questSpots.firstOrNull { spot ->
                                val d = hypot(
                                    (tap.x - spot.position.x).toDouble(),
                                    (tap.y - spot.position.y).toDouble()
                                ).toFloat()
                                d <= spot.radiusPx
                            }
                            if (hit != null) {
                                selectedQuest = hit
                            } else {
                                // 2) altrimenti muovi verso tap
                                target = tap
                            }
                        }
                    }
            ) {
                // sfondo
                drawRect(color = bgColor, size = size)

                // alberi (placeholder)
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

                // altri player (placeholder: pos deterministic da uid)
                val members = session?.members.orEmpty()
                members.forEach { m ->
                    val p = uidToSpawn(m.uid)
                    drawCircle(
                        color = scheme.primary.copy(alpha = 0.55f),
                        radius = 18f,
                        center = p
                    )
                }

                // player locale
                drawCircle(
                    color = scheme.onSurface,
                    radius = 22f,
                    center = avatar
                )

                // piccolo target indicator
                val tSize = 10f
                drawRect(
                    color = scheme.onSurface.copy(alpha = 0.35f),
                    topLeft = Offset(target.x - tSize / 2, target.y - tSize / 2),
                    size = Size(tSize, tSize)
                )
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
                }
            ) { Text("Reset") }

            Button(
                onClick = {
                    // avvia la prima quest non completata
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

// posizione deterministica per uid (placeholder multiplayer)
private fun uidToSpawn(uid: String): Offset {
    val h = uid.hashCode()
    val x = 150f + (kotlin.math.abs(h % 700) % 700)
    val y = 220f + (kotlin.math.abs((h / 7) % 1100) % 1100)
    return Offset(x, y)
}
