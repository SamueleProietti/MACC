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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import it.sapienza.forestanimalsgame.R

class RegisterActivity : ComponentActivity() {

    private val viewModel: RegisterViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // se vuoi puoi controllare i permessi qui
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
                onRegister = { viewModel.saveProfile() },   // ✅ FIX: prima era completeRegistration()
                onFinish = { finish() },
                viewModel = viewModel
            )
        }
    }

    private fun fetchLocation() {
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).addOnSuccessListener { location ->
            if (location != null) viewModel.setLocation(location)
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
    val loading by viewModel.loading.observeAsState(false)
    val done by viewModel.done.observeAsState(false)

    // ✅ quando entri/ri-entri nella schermata, ricarica dal backend
    LaunchedEffect(Unit) {
        viewModel.loadMyProfile()
    }

    // se vuoi chiudere la pagina dopo il salvataggio
    LaunchedEffect(done) {
        if (done) onFinish()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("Profilo", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Text(
            "Location: " + (location?.let { "${it.latitude}, ${it.longitude}" } ?: "non disponibile")
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRefreshLocation) { Text("Aggiorna posizione") }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onTakePhoto) { Text("Scatta foto") }

        Spacer(Modifier.height(8.dp))
        Text("Foto: " + (if (photo != null || !photoUrl.isNullOrBlank()) "OK" else "manca"))

        // ✅ mostra la foto: prima bitmap locale, altrimenti URL dal backend
        when {
            photo != null -> {
                Spacer(Modifier.height(12.dp))
                Image(
                    bitmap = photo!!.asImageBitmap(),
                    contentDescription = "Foto profilo",
                    modifier = Modifier.height(160.dp)
                )
            }

            !photoUrl.isNullOrBlank() -> {
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Foto profilo",
                    modifier = Modifier.height(160.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Scegli il tuo avatar:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvatarChoice(
                id = "fox",
                resId = R.drawable.av_fox,
                selected = avatarId == "fox",
                onClick = { viewModel.setAvatar("fox") }
            )
            AvatarChoice(
                id = "deer",
                resId = R.drawable.av_deer,
                selected = avatarId == "deer",
                onClick = { viewModel.setAvatar("deer") }
            )
            AvatarChoice(
                id = "wolf",
                resId = R.drawable.av_wolf,
                selected = avatarId == "wolf",
                onClick = { viewModel.setAvatar("wolf") }
            )
            AvatarChoice(
                id = "bear",
                resId = R.drawable.av_bear,
                selected = avatarId == "bear",
                onClick = { viewModel.setAvatar("bear") }
            )
            AvatarChoice(
                id = "boar",
                resId = R.drawable.av_boar,
                selected = avatarId == "boar",
                onClick = { viewModel.setAvatar("boar") }
            )
        }

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onRegister, enabled = !loading) {
            Text(if (loading) "Salvataggio..." else "Salva profilo")
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
