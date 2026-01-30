package it.sapienza.forestanimalsgame.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import it.sapienza.forestanimalsgame.R
import kotlin.random.Random

// Modello per un oggetto scenico (Albero, Roccia, Fungo...)
data class SceneryObject(
    val id: Int,
    val type: SceneryType,
    val x: Float, // Posizione X nel mondo (px)
    val y: Float, // Posizione Y nel mondo (px)
    val scale: Float = 1f
)

// Enum che elenca tutti i tipi di oggetti che possiamo piazzare
enum class SceneryType {
    TREE_PINE, TREE_OAK, ROCK, MUSHROOM_RED, MUSHROOM_GROUP, BUSH, LOG, SIGN
}

// Funzione helper per caricare tutti i PNG necessari (da chiamare nel GameScreen)
@Composable
fun rememberForestResources(): Map<SceneryType, ImageBitmap> {
    // Qui carichiamo le risorse una volta sola per non rallentare il disegno
    val pine = ImageBitmap.imageResource(R.drawable.tree) // tree.png
    val oak = ImageBitmap.imageResource(R.drawable.chioma1)  // Se hai un altro albero, cambialo qui
    val bush = ImageBitmap.imageResource(R.drawable.chiome2) // Se hai un cespuglio, cambialo qui (es. 2chiome.png)
    val rock = ImageBitmap.imageResource(R.drawable.roccia) // roccia.png
    val mushRed = ImageBitmap.imageResource(R.drawable.funghetto1) // 1funghetto.png
    val mushGroup = ImageBitmap.imageResource(R.drawable.funghi3) // 3funghi.png
    val log = ImageBitmap.imageResource(R.drawable.tronco) // tronco.png
    val sign = ImageBitmap.imageResource(R.drawable.cartello) // cartello.png

    return remember {
        mapOf(
            SceneryType.TREE_PINE to pine,
            SceneryType.TREE_OAK to oak,
            SceneryType.BUSH to bush,
            SceneryType.ROCK to rock,
            SceneryType.MUSHROOM_RED to mushRed,
            SceneryType.MUSHROOM_GROUP to mushGroup,
            SceneryType.LOG to log,
            SceneryType.SIGN to sign
        )
    }
}

// Funzione helper per caricare la tile del terreno
@Composable
fun rememberTerrainTile(): ImageBitmap {
    return ImageBitmap.imageResource(R.drawable.prato) // prato.png (32x32)
}

// Funzione di disegno principale (da usare nel Canvas di GameScreen)
fun DrawScope.drawForestMap(
    worldSize: Size,
    terrainTile: ImageBitmap,
    scenery: List<SceneryObject>,
    resources: Map<SceneryType, ImageBitmap>,
    visibleRect: androidx.compose.ui.geometry.Rect // Ottimizzazione: disegna solo ciò che si vede
) {
    // 1. Disegna il TERRENO (Tiling)
    val tileSize = 32f // Dimensione della tile (prato.png)
    
    // Calcoliamo quali tile sono visibili (Culling) per non disegnare 4000x4000 px inutilmente
    // Se visibleRect è tutto il mondo, disegna tutto.
    // Con visibleRect ottimizziamo disegnando solo le tile che l'utente vede.
    val startCol = (visibleRect.left / tileSize).toInt().coerceAtLeast(0)
    val endCol = (visibleRect.right / tileSize).toInt().coerceAtMost((worldSize.width / tileSize).toInt())
    val startRow = (visibleRect.top / tileSize).toInt().coerceAtLeast(0)
    val endRow = (visibleRect.bottom / tileSize).toInt().coerceAtMost((worldSize.height / tileSize).toInt())

    for (row in startRow..endRow) {
        for (col in startCol..endCol) {
            drawImage(
                image = terrainTile,
                dstOffset = IntOffset((col * tileSize).toInt(), (row * tileSize).toInt()),
                dstSize = IntSize(tileSize.toInt(), tileSize.toInt())
            )
        }
    }

    // 2. Disegna gli OGGETTI SCENICI (2.5D Sorting)
    // Filtriamo solo quelli visibili + un margine per non farli sparire di colpo
    val margin = 200f 
    val visibleScenery = scenery.filter { 
        it.x >= visibleRect.left - margin && it.x <= visibleRect.right + margin &&
        it.y >= visibleRect.top - margin && it.y <= visibleRect.bottom + margin
    }

    // Ordiniamo per Y (quelli in basso coprono quelli in alto)
    val sortedScenery = visibleScenery.sortedBy { it.y }

    sortedScenery.forEach { obj ->
        val image = resources[obj.type] ?: return@forEach
        
        // Centriamo l'immagine sulla posizione X,Y
        // L'ancora Y deve essere ai "piedi" dell'oggetto per l'effetto 2.5D corretto
        val width = image.width.toFloat() * obj.scale
        val height = image.height.toFloat() * obj.scale
        
        val left = obj.x - (width / 2f)
        val top = obj.y - height // Disegna verso l'alto rispetto al punto di ancoraggio
        
        drawImage(
            image = image,
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(width.toInt(), height.toInt())
        )
    }
}

// Generatore procedurale (da chiamare una volta sola in GameScreen)
fun generateForestScenery(worldSize: Size, count: Int): List<SceneryObject> {
    val list = mutableListOf<SceneryObject>()
    val rnd = Random(1234) // Seed fisso = foresta sempre uguale per tutti

    for (i in 0 until count) {
        val type = when (rnd.nextInt(100)) {
            in 0..30 -> SceneryType.TREE_PINE
            in 31..50 -> SceneryType.TREE_OAK
            in 51..60 -> SceneryType.BUSH
            in 61..75 -> SceneryType.MUSHROOM_GROUP
            in 76..85 -> SceneryType.ROCK
            in 86..90 -> SceneryType.LOG
            in 91..95 -> SceneryType.MUSHROOM_RED
            else -> SceneryType.SIGN
        }

        // Posizione casuale
        val x = rnd.nextFloat() * worldSize.width
        val y = rnd.nextFloat() * worldSize.height
        
        // Scala variabile per dare naturalezza
        val scale = 0.8f + rnd.nextFloat() * 0.4f 

        list.add(SceneryObject(i, type, x, y, scale))
    }
    return list
}