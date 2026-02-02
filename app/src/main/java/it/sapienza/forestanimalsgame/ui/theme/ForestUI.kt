package it.sapienza.forestanimalsgame.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.sapienza.forestanimalsgame.R

// --- BOTTONE RPG ---
@Composable
fun ForestButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(60.dp)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier.alpha(0.6f))
    ) {
        Image(
            painter = painterResource(id = R.drawable.plank_wide_left),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        Text(
            text = text,
            color = if (enabled) Color(0xFFFFF8E1) else Color.LightGray,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            style = MaterialTheme.typography.labelLarge.copy(
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.8f),
                    offset = Offset(2f, 2f),
                    blurRadius = 3f
                )
            ),
            textAlign = TextAlign.Center
        )
    }
}

// --- SFONDO LOGIN ---
@Composable
fun LoginBackgroundContainer(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background_login),
            contentDescription = null,
            // CAMBIATO DA Crop A FillBounds per mostrare tutta l'immagine
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )
        content()
    }
}

// --- SFONDO LOBBY/HOME (Sfocato + Padding Sistema) ---
@Composable
fun LobbyBackgroundContainer(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background_lobby_blurred), // Crea questa immagine sfocata
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Velo scuro per leggere i testi
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

        // Risolve il problema della scritta coperta dalla fotocamera
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            content()
        }
    }
}

// Stile testo bianco con ombra
val ForestTextStyle = androidx.compose.ui.text.TextStyle(
    color = Color.White,
    fontWeight = FontWeight.Bold,
    shadow = Shadow(Color.Black, Offset(2f, 2f), 4f)
)