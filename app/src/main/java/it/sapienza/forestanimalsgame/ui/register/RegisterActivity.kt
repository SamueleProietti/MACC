package it.sapienza.forestanimalsgame.ui.register

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import it.sapienza.forestanimalsgame.R
import it.sapienza.forestanimalsgame.ui.theme.ForestButton
import it.sapienza.forestanimalsgame.ui.theme.LobbyBackgroundContainer
import it.sapienza.forestanimalsgame.ui.theme.ForestTextStyle

class RegisterActivity : ComponentActivity() {

    private val viewModel: RegisterViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ -> }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) viewModel.setPhoto(bitmap)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        permissionsLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.CAMERA)
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
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location -> if (location != null) viewModel.setLocation(location) }
        } catch (e: SecurityException) { }
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

    LaunchedEffect(Unit) { viewModel.loadMyProfile() }
    LaunchedEffect(done) { if (done) onFinish() }

    LobbyBackgroundContainer {

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFD54F))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Profilo Giocatore", style = MaterialTheme.typography.headlineMedium.merge(ForestTextStyle))
                Spacer(Modifier.height(24.dp))

                // --- POSIZIONE (Senza box nero, con coordinate) ---
                if (location != null) {
                    Text(
                        text = "Lat: ${location!!.latitude}",
                        style = ForestTextStyle,
                        color = Color.White
                    )
                    Text(
                        text = "Lng: ${location!!.longitude}",
                        style = ForestTextStyle,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "Posizione mancante",
                        style = ForestTextStyle,
                        color = Color.Red
                    )
                }

                Spacer(Modifier.height(12.dp))

                ForestButton(text = "Aggiorna Posizione", onClick = onRefreshLocation, modifier = Modifier.width(200.dp).height(45.dp))

                Spacer(Modifier.height(24.dp))

                // --- FOTO ---
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .border(3.dp, Color(0xFF8D6E63)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        photo != null -> Image(bitmap = photo!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        !photoUrl.isNullOrBlank() -> SubcomposeAsyncImage(model = photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else -> Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(60.dp), tint = Color.LightGray)
                    }
                }

                Spacer(Modifier.height(12.dp))
                ForestButton(text = "Scatta Foto", onClick = onTakePhoto, modifier = Modifier.width(200.dp).height(45.dp))

                Spacer(Modifier.height(32.dp))

                // --- AVATAR ---
                Text("Scegli il tuo Avatar:", style = ForestTextStyle)
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
                    Text(error!!, color = Color.Red, modifier = Modifier.background(Color.White).padding(4.dp))
                }

                Spacer(Modifier.height(40.dp))

                ForestButton(
                    text = "SALVA PROFILO",
                    onClick = onRegister,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AvatarChoice(id: String, resId: Int, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) Color(0xFFFFD54F) else Color.Transparent
    Box(
        modifier = Modifier
            .size(54.dp)
            .border(3.dp, borderColor, MaterialTheme.shapes.small)
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Image(painter = painterResource(resId), contentDescription = id, modifier = Modifier.fillMaxSize())
    }
}