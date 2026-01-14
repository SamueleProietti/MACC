package it.sapienza.forestanimalsgame.ui.register

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class RegisterActivity : ComponentActivity() {

    private val viewModel: RegisterViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val locationOk = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (locationOk) fetchLocation()
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
                onRegister = { viewModel.completeRegistration() },
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
    viewModel: RegisterViewModel
) {
    val location by viewModel.location.observeAsState(null)
    val photo by viewModel.photo.observeAsState(null)
    val error by viewModel.error.observeAsState(null)
    val done by viewModel.done.observeAsState(false)
    LaunchedEffect(done) {
        if (done) finish()
    }


    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Registrazione", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Text("Location: " + (location?.let { "${it.latitude}, ${it.longitude}" } ?: "non disponibile"))
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRefreshLocation) { Text("Aggiorna posizione") }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onTakePhoto) { Text("Scatta foto") }
        Spacer(Modifier.height(8.dp))
        Text("Foto: " + (if (photo != null) "OK" else "manca"))

        photo?.let {
            Spacer(Modifier.height(12.dp))
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Foto profilo"
            )
        }

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onRegister, enabled = !loading) { Text(if (loading) "Salvataggio..." else "Completa registrazione") }

    }
}