package it.sapienza.forestanimalsgame.ui.lobby

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.forestanimalsgame.data.model.ChatMessage
import it.sapienza.forestanimalsgame.data.model.Member
import it.sapienza.forestanimalsgame.data.model.Session

@Composable
fun LobbyEntryScreen(viewModel: LobbyViewModel) {
    val loading by viewModel.loading.observeAsState(false)
    val error by viewModel.error.observeAsState(null)

    var code by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Lobby", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Codice sessione") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.createSession() },
                enabled = !loading,
                modifier = Modifier.weight(1f)
            ) {
                Text("Crea sessione")
            }

            Button(
                onClick = { viewModel.joinSession(code) },
                enabled = !loading,
                modifier = Modifier.weight(1f)
            ) {
                Text("Entra")
            }
        }

        if (loading) {
            CircularProgressIndicator()
        }

        if (!error.isNullOrBlank()) {
            Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Suggerimento: per ora il codice è l’ID del documento Firestore.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun LobbyScreen(viewModel: LobbyViewModel, sessionId: String) {
    val session by viewModel.session.observeAsState(null)
    val messages by viewModel.messages.observeAsState(emptyList())
    val error by viewModel.error.observeAsState(null)

    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val isHost = (session?.hostUid != null && session?.hostUid == currentUid)
    val canStart = (session?.members?.size ?: 0) >= 2 && (session?.status == "LOBBY") && isHost

    var messageText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Sessione", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(sessionId, style = MaterialTheme.typography.bodySmall)
                Text("Stato: ${session?.status ?: "..." }", style = MaterialTheme.typography.bodySmall)
            }

            OutlinedButton(onClick = { viewModel.leaveSession() }) {
                Text("Esci")
            }
        }

        // Membri
        MembersCard(session)

        // Chat
        Text("Chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nessun messaggio")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubble(msg, currentUid)
                    }
                }
            }
        }

        // Input chat
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("Messaggio") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = {
                    viewModel.sendMessage(messageText)
                    messageText = ""
                },
                enabled = messageText.isNotBlank()
            ) {
                Text("Invia")
            }
        }

        // Start
        Button(
            onClick = { viewModel.startGameIfHost() },
            enabled = canStart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isHost) "Start" else "Attendi host")
        }

        if (!error.isNullOrBlank()) {
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MembersCard(session: Session?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Membri (${session?.members?.size ?: 0}/4)", fontWeight = FontWeight.Bold)
            val members: List<Member> = session?.members ?: emptyList()

            if (members.isEmpty()) {
                Text("In attesa di membri...", style = MaterialTheme.typography.bodySmall)
            } else {
                members.forEach { m ->
                    Text("• ${m.displayName} (${m.avatar})", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Start abilitato solo se siete almeno 2 e solo l’host può avviarlo.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, currentUid: String?) {
    val mine = (currentUid != null && msg.senderUid == currentUid)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        Text(
            text = msg.senderName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = msg.text,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
