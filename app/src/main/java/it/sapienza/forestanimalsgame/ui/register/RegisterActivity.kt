package it.sapienza.forestanimalsgame.ui.register

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import it.sapienza.forestanimalsgame.R

class RegisterActivity : ComponentActivity() {

    private val viewModel: RegisterViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // Qui potresti gestire il caso in cui i permessi non vengono dati
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) viewModel.setPhoto(bitmap)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
            )
        )

        setContent {
            RegisterScreen(
                onTakePhoto = { cameraLauncher.launch(null) },
                onRefreshLocation = { fetchLocation() },
                onRegister = { viewModel.saveProfile() },
                onFinish = { finish() },
                viewModel = viewModel
            )
        }
    }

    private fun fetchLocation() {
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { location ->
                if (location != null) viewModel.setLocation(location)
            }
        } catch (e: SecurityException) {
            // Gestione mancanza permessi
        }
    }
}

@Composable
fun RegisterScreen(
    onTakePhoto: () -> Unit,
    onRefreshLocation: () -> Unit,
    onRegister: () -> Unit,
    onFinish: () -> Unit,
    viewModel: RegisterViewModel
) {
    val location by viewModel.location.observeAsState(null)
    val photo by viewModel.photo.observeAsState(null)
    val photoUrl by viewModel.photoUrl.observeAsState(null)
    val avatarId by viewModel.avatarId.observeAsState("fox")
    val error by viewModel.error.observeAsState(null)
    val done by viewModel.done.observeAsState(false)
    val loading by viewModel.loading.observeAsState(true)

    // Carica profilo al primo avvio
    LaunchedEffect(Unit) {
        viewModel.loadMyProfile()
    }

    // Chiude l'activity se il salvataggio è completato
    LaunchedEffect(done) {
        if (done) onFinish()
    }

    // 1. SE STA CARICANDO, MOSTRA SOLO LA ROTELLA
    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        // 2. ALTRIMENTI MOSTRA IL CONTENUTO
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Profilo", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            // --- SEZIONE POSIZIONE ---
            if (location != null) {
                Text(
                    "Posizione: ${location?.latitude}, ${location?.longitude}",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text("Posizione non disponibile", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRefreshLocation) { Text("Aggiorna posizione") }
            // --------------------------

            Spacer(Modifier.height(24.dp))

            Button(onClick = onTakePhoto) { Text("Scatta foto") }

            Spacer(Modifier.height(8.dp))

            val hasPhoto = photo != null || !photoUrl.isNullOrBlank()
            Text("Foto: " + (if (hasPhoto) "OK" else "Obbligatoria"))

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .height(160.dp)
                    .width(160.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center
            ) {
                // Definisco il placeholder
                val PlaceholderIcon = @Composable {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Manca foto",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Nessuna foto", style = MaterialTheme.typography.bodySmall)
                    }
                }

                when {
                    // Caso 1: Nuova foto appena scattata (locale)
                    photo != null -> {
                        Image(
                            bitmap = photo!!.asImageBitmap(),
                            contentDescription = "Foto profilo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    // Caso 2: Foto esistente dal server
                    !photoUrl.isNullOrBlank() -> {
                        // ✅ FIX: Usiamo SubcomposeAsyncImage per supportare il blocco 'error' composable
                        SubcomposeAsyncImage(
                            model = photoUrl,
                            contentDescription = "Foto profilo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = { PlaceholderIcon() } // Ora questo è valido!
                        )
                    }
                    // Caso 3: Nessuna foto
                    else -> {
                        PlaceholderIcon()
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Scegli il tuo avatar:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AvatarChoice("fox", R.drawable.av_fox, avatarId == "fox") { viewModel.setAvatar("fox") }
                AvatarChoice("deer", R.drawable.av_deer, avatarId == "deer") { viewModel.setAvatar("deer") }
                AvatarChoice("wolf", R.drawable.av_wolf, avatarId == "wolf") { viewModel.setAvatar("wolf") }
                AvatarChoice("bear", R.drawable.av_bear, avatarId == "bear") { viewModel.setAvatar("bear") }
                AvatarChoice("boar", R.drawable.av_boar, avatarId == "boar") { viewModel.setAvatar("boar") }
            }

            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                //Text(error!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = onRegister, enabled = !loading) {
                Text(if (loading) "Salvataggio..." else "Salva profilo")
            }
        }
    }
}

@Composable
private fun AvatarChoice(
    id: String,
    resId: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderWidth = if (selected) 2.dp else 0.dp
    Image(
        painter = painterResource(resId),
        contentDescription = id,
        modifier = Modifier
            .size(48.dp)
            .border(borderWidth, MaterialTheme.colorScheme.primary)
            .clickable { onClick() }
            .padding(2.dp)
    )
}