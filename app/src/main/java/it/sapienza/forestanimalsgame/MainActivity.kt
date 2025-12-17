package it.sapienza.forestanimalsgame

import android.os.Bundle
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        credentialManager = CredentialManager.create(this)

        setContent {
            ForestAnimalsGameTheme {
                val currentUser = authViewModel.currentUser
                val isLoading = authViewModel.isLoading
                val errorMessage = authViewModel.errorMessage

                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
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
                                onLogout = { authViewModel.logout() }
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
    onLogout: () -> Unit
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
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLogout) {
            Text("Logout")
        }
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
