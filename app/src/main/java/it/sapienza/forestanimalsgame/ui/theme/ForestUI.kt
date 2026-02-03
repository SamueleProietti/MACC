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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

// --- SFONDO LOGIN (Nitido) ---
@Composable
fun LoginBackgroundContainer(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background_login),
            contentDescription = null,
            contentScale = ContentScale.FillBounds, // Adatta tutto allo schermo
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
            painter = painterResource(id = R.drawable.background_lobby_blurred),
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

// --- POPUP STILE RPG (AGGIUNTO ORA) ---
@Composable
fun ForestDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    confirmText: String,
    confirmAction: () -> Unit,
    dismissText: String? = null,
    dismissAction: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            // Sfondo Cartello (sign_large)
            Image(
                painter = painterResource(id = R.drawable.sign_large),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .width(350.dp)
                    .height(300.dp)
            )

            // Contenuto Testo e Bottoni
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .width(300.dp)
                    .padding(16.dp)
            ) {
                // Titolo (Oro con Ombra)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.6f),
                            offset = Offset(2f, 2f),
                            blurRadius = 2f
                        )
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFE082), // ORO
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Testo Corpo (Bianco Panna)
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            offset = Offset(1f, 1f),
                            blurRadius = 1f
                        )
                    ),
                    color = Color(0xFFFFF8E1),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Bottoni
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (dismissText != null && dismissAction != null) {
                        ForestButton(text = dismissText, onClick = dismissAction, modifier = Modifier.width(110.dp), fontSize = 14.sp)
                    }

                    ForestButton(text = confirmText, onClick = confirmAction, modifier = Modifier.width(110.dp), fontSize = 14.sp)
                }
            }
        }
    }
}

// Stile testo bianco con ombra
val ForestTextStyle = androidx.compose.ui.text.TextStyle(
    color = Color.White,
    fontWeight = FontWeight.Bold,
    shadow = Shadow(Color.Black, Offset(2f, 2f), 4f)
)