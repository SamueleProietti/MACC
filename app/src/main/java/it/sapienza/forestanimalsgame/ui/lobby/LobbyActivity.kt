package it.sapienza.forestanimalsgame.ui.lobby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import it.sapienza.forestanimalsgame.ui.theme.ForestAnimalsGameTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding

class LobbyActivity : ComponentActivity() {

    private val lobbyViewModel: LobbyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // ----------------------------

        setContent {
            ForestAnimalsGameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {

                    // Init: Avatar e Resume
                    LaunchedEffect(Unit) {
                        lobbyViewModel.loadMyAvatarId()
                        lobbyViewModel.resumeMyActiveSession()
                    }

                    // Variabili di stato
                    val sessionId by lobbyViewModel.sessionId.observeAsState(null)
                    val session by lobbyViewModel.session.observeAsState(null)
                    val avatarId by lobbyViewModel.avatarId.observeAsState("fox")
                    
                    val gameState by lobbyViewModel.gameState.observeAsState(null)
                    val gameStateLoaded by lobbyViewModel.gameStateLoaded.observeAsState(false)

                    // Trigger caricamento dati quando si entra IN_GAME
                    LaunchedEffect(sessionId, session?.status) {
                        if (!sessionId.isNullOrBlank() && session?.status == "IN_GAME") {
                            lobbyViewModel.loadMyGameState(sessionId!!)
                        }
                    }

                    // UI Logic: UN SOLO blocco 'when'
                    when {
                        sessionId.isNullOrBlank() -> {
                            LobbyEntryScreen(viewModel = lobbyViewModel)
                        }

                        session?.status == "IN_GAME" -> {
                            // 1. Se stiamo caricando i dati, mostra il cerchio che gira
                            if (!gameStateLoaded) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Caricamento partita...", modifier = Modifier.padding(top = 40.dp))
                                }
                            } else {
                                // 2. Solo quando i dati ci sono, mostra il gioco
                                it.sapienza.forestanimalsgame.ui.game.GameScreen(
                                    sessionId = sessionId!!,
                                    session = session,
                                    avatarId = avatarId,
                                    initialGameState = gameState, // Qui arriveranno i dati corretti
                                    onAutoSave = { st -> lobbyViewModel.saveMyGameState(sessionId!!, st) },
                                    onStop = { state ->
                                        lifecycleScope.launch {
                                            lobbyViewModel.saveMyGameStateNow(sessionId!!, state)
                                            finish()
                                        }
                                    },
                                    onLeave = { lobbyViewModel.leaveSession() }
                                )
                            }
                        }

                        else -> {
                            LobbyScreen(
                                viewModel = lobbyViewModel,
                                sessionId = sessionId!!
                            )
                        }
                    }
                    
                    
                }
            }
        }
    }
}