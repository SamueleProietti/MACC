package it.sapienza.forestanimalsgame.ui.lobby

import android.content.Intent // <--- IMPORTANTE PER CONDIVIDERE
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.forestanimalsgame.data.model.ChatMessage
import it.sapienza.forestanimalsgame.data.model.Member
import it.sapienza.forestanimalsgame.data.model.Session
import it.sapienza.forestanimalsgame.ui.theme.ForestButton
import it.sapienza.forestanimalsgame.ui.theme.LobbyBackgroundContainer
import it.sapienza.forestanimalsgame.ui.theme.ForestTextStyle

@Composable
fun LobbyEntryScreen(viewModel: LobbyViewModel) {
    val loading by viewModel.loading.observeAsState(false)
    val error by viewModel.error.observeAsState(null)
    var code by rememberSaveable { mutableStateOf("") }

    LobbyBackgroundContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            Text("Lobby Multiplayer", style = MaterialTheme.typography.headlineMedium.merge(ForestTextStyle))

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Session Code", color = Color.White) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD54F),
                    unfocusedBorderColor = Color.White
                )
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ForestButton(text = "Create session", onClick = { viewModel.createSession() }, enabled = !loading, modifier = Modifier.weight(1f))
                ForestButton(text = "Join", onClick = { viewModel.joinSession(code) }, enabled = !loading, modifier = Modifier.weight(1f))
            }

            if (loading) CircularProgressIndicator(color = Color(0xFFFFD54F))

            if (!error.isNullOrBlank()) {
                Text(text = error!!, color = Color.Red, modifier = Modifier.background(Color.White.copy(alpha=0.8f)).padding(8.dp))
            }
        }
    }
}

@Composable
fun LobbyScreen(viewModel: LobbyViewModel, sessionId: String) {
    val session by viewModel.session.observeAsState(null)
    val messages by viewModel.messages.observeAsState(emptyList())
    val error by viewModel.error.observeAsState(null)
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid
    val isHost = (currentUid != null && session?.hostUid == currentUid)
    val canStart = (session?.members?.size ?: 0) >= 1 && (session?.status == "LOBBY") && isHost
    var messageText by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current

    LobbyBackgroundContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // --- INFO SESSIONE + TASTO CONDIVIDI ---
            Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha=0.6f))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Session Code:", style = ForestTextStyle, fontSize = 14.sp)
                        Text(sessionId, color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    // TASTO CONDIVIDI (SHARE SHEET)
                    ForestButton(
                        text = "Share", // Era "Copia"
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Play with me on Forest Quest! Code: $sessionId")
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Invite friends")
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier.width(100.dp).height(40.dp),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    ForestButton(
                        text = "Exit",
                        onClick = { viewModel.leaveSession() },
                        modifier = Modifier.width(70.dp).height(40.dp),
                        fontSize = 12.sp
                    )
                }
            }

            MembersCard(session)
            Text("Chat", style = ForestTextStyle)

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCF8C6).copy(alpha=0.9f))
            ) {
                if (messages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No messages", color = Color.DarkGray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages) { msg -> ChatBubble(msg, currentUid) }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Message", color = Color.White) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFFFFD54F), unfocusedBorderColor = Color.White)
                )
                ForestButton(text = "Send", onClick = { viewModel.sendMessage(messageText); messageText = "" }, enabled = messageText.isNotBlank(), modifier = Modifier.width(80.dp))
            }

            ForestButton(text = if (isHost) "START GAME" else "Wait for the Host...", onClick = { viewModel.startGameIfHost() }, enabled = canStart, modifier = Modifier.fillMaxWidth())

            if (!error.isNullOrBlank()) Text(text = error!!, color = Color.Red, modifier = Modifier.background(Color.White).padding(4.dp))
        }
    }
}

@Composable
private fun MembersCard(session: Session?) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha=0.5f))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Members (${session?.members?.size ?: 0}/4)", style = ForestTextStyle)
            session?.members?.forEach { m -> Text("• ${m.displayName} (${m.avatar})", color = Color.White) } ?: Text("Waiting...", color = Color.LightGray)
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, currentUid: String?) {
    val mine = (currentUid != null && msg.senderUid == currentUid)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Text(text = msg.senderName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.DarkGray)
        Surface(shadowElevation = 1.dp, shape = MaterialTheme.shapes.medium, color = if(mine) Color(0xFFDCF8C6) else Color.White) {
            Text(text = msg.text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.Black)
        }
    }
}