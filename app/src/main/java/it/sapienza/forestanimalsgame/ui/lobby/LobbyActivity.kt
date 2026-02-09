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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import it.sapienza.forestanimalsgame.ui.theme.LobbyBackgroundContainer
import it.sapienza.forestanimalsgame.ui.theme.ForestTextStyle

class LobbyActivity : ComponentActivity() {

    private val lobbyViewModel: LobbyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        setContent {
            ForestAnimalsGameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {

                    LaunchedEffect(Unit) {
                        lobbyViewModel.loadMyAvatarId()
                        lobbyViewModel.resumeMyActiveSession()
                    }

                    // Osserva il meteo dal ViewModel
                    val weatherCondition by lobbyViewModel.weatherCondition.observeAsState("clear")
                    val sessionId by lobbyViewModel.sessionId.observeAsState(null)
                    val session by lobbyViewModel.session.observeAsState(null)
                    val avatarId by lobbyViewModel.avatarId.observeAsState("fox")

                    val gameState by lobbyViewModel.gameState.observeAsState(null)
                    val gameStateLoaded by lobbyViewModel.gameStateLoaded.observeAsState(false)

                    // 1. Appena entra in gioco, carica il MIO stato (posizione, ecc.)
                    LaunchedEffect(sessionId, session?.status) {
                        if (!sessionId.isNullOrBlank() && session?.status == "IN_GAME") {
                            lobbyViewModel.loadMyGameState(sessionId!!)
                            lobbyViewModel.loadRealWeather()
                        }
                    }

                    LaunchedEffect(gameStateLoaded) {
                        if (gameStateLoaded && !sessionId.isNullOrBlank()) {
                            lobbyViewModel.listenToGameState(sessionId!!)
                        }
                    }

                    when {
                        sessionId.isNullOrBlank() -> {
                            LobbyEntryScreen(viewModel = lobbyViewModel)
                        }

                        session?.status == "IN_GAME" -> {
                            // 1. CARICAMENTO DATI GIOCO (Con Sfondo Sfocato)
                            if (!gameStateLoaded) {
                                LobbyBackgroundContainer {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = Color(0xFFFFD54F))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Caricamento partita...",
                                            style = ForestTextStyle,
                                            modifier = Modifier.padding(top = 60.dp)
                                        )
                                    }
                                }
                            } else {
                                // 2. GIOCO CARICATO
                                it.sapienza.forestanimalsgame.ui.game.GameScreen(
                                    sessionId = sessionId!!,
                                    session = session,
                                    avatarId = avatarId,
                                    initialGameState = gameState,
                                    currentWeather = weatherCondition,
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