package it.sapienza.forestanimalsgame

import android.os.Bundle
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseUser
import it.sapienza.forestanimalsgame.ui.auth.AuthViewModel
import it.sapienza.forestanimalsgame.ui.theme.ForestAnimalsGameTheme
import it.sapienza.forestanimalsgame.ui.register.RegisterActivity
import it.sapienza.forestanimalsgame.ui.lobby.LobbyActivity
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import it.sapienza.forestanimalsgame.data.repository.ProfileRepositoryImpl

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    // Creiamo un'istanza del repository per controllare il profilo
    private val profileRepository = ProfileRepositoryImpl()
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- BLOCCA LA ROTAZIONE DELLO SCHERMO ---
        // Questo impedisce all'app di ruotare e riavviarsi
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // -----------------------------------------

        credentialManager = CredentialManager.create(this)

        setContent {
            ForestAnimalsGameTheme {
                val currentUser = authViewModel.currentUser
                val isLoading = authViewModel.isLoading
                val errorMessage = authViewModel.errorMessage
                // Stato per il Dialog di avviso
                var showProfileAlert by remember { mutableStateOf(false) }
                // Stato per il caricamento durante il controllo profilo
                var isCheckingProfile by remember { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxSize()) {

                    // Mostra Alert se l'utente prova a giocare senza profilo
                    if (showProfileAlert) {
                        AlertDialog(
                            onDismissRequest = { showProfileAlert = false },
                            title = { Text("Profilo Incompleto") },
                            text = { Text("Per entrare in Lobby devi prima completare il profilo (Posizione e Foto).") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showProfileAlert = false
                                    startActivity(Intent(this@MainActivity, RegisterActivity::class.java))
                                }) {
                                    Text("Vai al Profilo")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showProfileAlert = false }) {
                                    Text("Annulla")
                                }
                            }
                        )
                    }
                    
                    when {
                        isLoading || isCheckingProfile -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        currentUser == null -> {
                            LoginScreen(
                                errorMessage = errorMessage,
                                onSignInClick = {
                                    lifecycleScope.launch {
                                        val idToken = requestGoogleIdToken()
                                        if (idToken != null) {
                                            authViewModel.signInWithGoogleIdToken(idToken)
                                        }
                                    }
                                }
                            )
                        }

                        else -> {
                            HomeScreen(
                                user = currentUser,
                                onLogout = { authViewModel.logout() },
                                onCompleteProfile = {
                                    startActivity(Intent(this, RegisterActivity::class.java))
                                },
                                onOpenLobby = {
                                    // 🚀 LOGICA DI CONTROLLO PRE-LOBBY
                                    lifecycleScope.launch {
                                        isCheckingProfile = true
                                        try {
                                            // Chiediamo al backend i dati del profilo
                                            val profile = profileRepository.getMyProfile()
                                            
                                            // Controlliamo se è completo (Ha foto? Ha coordinate?)
                                            val hasPhoto = !profile.photoUrl.isNullOrBlank()
                                            val hasLoc = profile.lat != null && profile.lng != null
                                            
                                            if (hasPhoto && hasLoc) {
                                                // Tutto ok, entra in Lobby
                                                startActivity(Intent(this@MainActivity, LobbyActivity::class.java))
                                            } else {
                                                // Profilo incompleto -> Mostra Popup
                                                showProfileAlert = true
                                            }
                                        } catch (e: Exception) {
                                            // Se il profilo non esiste (es. 404) o errore di rete -> Mostra Popup
                                            showProfileAlert = true
                                        } finally {
                                            isCheckingProfile = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun requestGoogleIdToken(): String? {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                request = request,
                context = this
            )
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential =
                    GoogleIdTokenCredential.createFrom(credential.data)
                googleIdTokenCredential.idToken
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun HomeScreen(
    user: FirebaseUser,
    onLogout: () -> Unit,
    onCompleteProfile: () -> Unit,
    onOpenLobby: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text("Benvenuto, ${user.displayName ?: "giocatore"}")
        Text(user.email ?: "", style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(12.dp))
        Button(onClick = onCompleteProfile) { Text("Profilo") }

        Spacer(Modifier.height(12.dp))
        Button(onClick = onOpenLobby) { Text("Gioca (Lobby)") }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onLogout) { Text("Logout") }
    }
}

@Composable
fun LoginScreen(
    errorMessage: String?,
    onSignInClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Forest Animals Game", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSignInClick) {
            Text("Sign in with Google")
        }
        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
