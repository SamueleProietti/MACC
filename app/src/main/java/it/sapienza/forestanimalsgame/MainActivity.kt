package it.sapienza.forestanimalsgame

import android.os.Bundle
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseUser
import it.sapienza.forestanimalsgame.ui.auth.AuthViewModel
import it.sapienza.forestanimalsgame.ui.theme.ForestAnimalsGameTheme
import it.sapienza.forestanimalsgame.ui.register.RegisterActivity
import it.sapienza.forestanimalsgame.ui.lobby.LobbyActivity
import kotlinx.coroutines.launch
import it.sapienza.forestanimalsgame.data.repository.ProfileRepositoryImpl
import it.sapienza.forestanimalsgame.ui.theme.ForestButton
import it.sapienza.forestanimalsgame.ui.theme.LoginBackgroundContainer
import it.sapienza.forestanimalsgame.ui.theme.LobbyBackgroundContainer
import it.sapienza.forestanimalsgame.ui.theme.ForestTextStyle

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val profileRepository = ProfileRepositoryImpl()
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        credentialManager = CredentialManager.create(this)

        setContent {
            ForestAnimalsGameTheme {
                val currentUser = authViewModel.currentUser
                val isLoading = authViewModel.isLoading
                val errorMessage = authViewModel.errorMessage
                var showProfileAlert by remember { mutableStateOf(false) }
                var isCheckingProfile by remember { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxSize()) {

                    if (showProfileAlert) {
                        AlertDialog(
                            onDismissRequest = { showProfileAlert = false },
                            title = { Text("Profilo Incompleto") },
                            text = { Text("Per entrare in Lobby devi prima completare il profilo (Posizione e Foto).") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showProfileAlert = false
                                    startActivity(Intent(this@MainActivity, RegisterActivity::class.java))
                                }) { Text("Vai al Profilo") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showProfileAlert = false }) { Text("Annulla") }
                            }
                        )
                    }

                    // --- LOGICA DI VISUALIZZAZIONE AGGIORNATA ---
                    when {
                        // 1. CARICAMENTO (Con Sfondo Sfocato)
                        isLoading || isCheckingProfile -> {
                            LobbyBackgroundContainer {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color(0xFFFFD54F))
                                }
                            }
                        }

                        // 2. NON LOGGATO (Login Screen)
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

                        // 3. LOGGATO (Home Screen)
                        else -> {
                            HomeScreen(
                                user = currentUser,
                                onLogout = { authViewModel.logout() },
                                onCompleteProfile = {
                                    startActivity(Intent(this, RegisterActivity::class.java))
                                },
                                onOpenLobby = {
                                    lifecycleScope.launch {
                                        isCheckingProfile = true
                                        try {
                                            val profile = profileRepository.getMyProfile()
                                            val hasPhoto = !profile.photoUrl.isNullOrBlank()
                                            val hasLoc = profile.lat != null && profile.lng != null
                                            if (hasPhoto && hasLoc) {
                                                startActivity(Intent(this@MainActivity, LobbyActivity::class.java))
                                            } else {
                                                showProfileAlert = true
                                            }
                                        } catch (e: Exception) {
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
            val result = credentialManager.getCredential(request = request, context = this)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else null
        } catch (e: Exception) { null }
    }
}

@Composable
fun HomeScreen(
    user: FirebaseUser,
    onLogout: () -> Unit,
    onCompleteProfile: () -> Unit,
    onOpenLobby: () -> Unit
) {
    LobbyBackgroundContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Benvenuto,", style = MaterialTheme.typography.headlineSmall.merge(ForestTextStyle))
            Text(
                text = user.displayName ?: "Viaggiatore",
                style = MaterialTheme.typography.headlineMedium.merge(ForestTextStyle),
                color = Color(0xFFFFD54F)
            )

            Spacer(Modifier.height(48.dp))
            ForestButton(text = "Profilo", onClick = onCompleteProfile, modifier = Modifier.fillMaxWidth(0.8f))
            Spacer(Modifier.height(16.dp))
            ForestButton(text = "Gioca (Lobby)", onClick = onOpenLobby, modifier = Modifier.fillMaxWidth(0.8f))
            Spacer(Modifier.height(32.dp))
            ForestButton(text = "Logout", onClick = onLogout, modifier = Modifier.fillMaxWidth(0.6f))
        }
    }
}

@Composable
fun LoginScreen(
    errorMessage: String?,
    onSignInClick: () -> Unit
) {
    LoginBackgroundContainer {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.background(Color.White.copy(alpha=0.8f)).padding(8.dp)
                )
                Spacer(Modifier.height(16.dp))
            }

            ForestButton(
                text = "Entra con Google",
                onClick = onSignInClick,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Spacer(modifier = Modifier.weight(0.60f))
        }
    }
}