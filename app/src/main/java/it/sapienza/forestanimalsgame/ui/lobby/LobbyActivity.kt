package it.sapienza.forestanimalsgame.ui.lobby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import it.sapienza.forestanimalsgame.ui.theme.ForestAnimalsGameTheme

class LobbyActivity : ComponentActivity() {

    private val lobbyViewModel: LobbyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ForestAnimalsGameTheme {
                Surface(modifier = Modifier.fillMaxSize()) {

                    val sessionId by lobbyViewModel.sessionId.observeAsState(null)
                    val session by lobbyViewModel.session.observeAsState(null)

                    when {
                        sessionId.isNullOrBlank() -> {
                            LobbyEntryScreen(viewModel = lobbyViewModel)
                        }

                        session?.status == "IN_GAME" -> {
                            it.sapienza.forestanimalsgame.ui.game.GameScreen(
                                sessionId = sessionId!!,
                                session = session,
                                onLeave = { lobbyViewModel.leaveSession() }
                            )
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
