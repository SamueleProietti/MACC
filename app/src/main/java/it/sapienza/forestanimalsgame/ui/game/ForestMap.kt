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
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.random.Random

// Modello per un oggetto scenico
data class SceneryObject(
    val id: Int,
    val type: SceneryType,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val collisionRadius: Float = 0f
)

enum class SceneryType {
    TREE_PINE, TREE_OAK, ROCK, MUSHROOM_RED, MUSHROOM_GROUP, BUSH, LOG, SIGN
}

@Composable
fun rememberForestResources(): Map<SceneryType, ImageBitmap> {
    val pine = ImageBitmap.imageResource(R.drawable.tree)
    val oak = ImageBitmap.imageResource(R.drawable.tree)
    val bush = ImageBitmap.imageResource(R.drawable.tree)
    val rock = ImageBitmap.imageResource(R.drawable.roccia)
    val mushRed = ImageBitmap.imageResource(R.drawable.funghetto1)
    val mushGroup = ImageBitmap.imageResource(R.drawable.funghi3)
    val log = ImageBitmap.imageResource(R.drawable.tronco)
    val sign = ImageBitmap.imageResource(R.drawable.cartello)

    return remember(pine, oak, bush, rock, mushRed, mushGroup, log, sign) {
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

@Composable
fun rememberTerrainTile(): ImageBitmap {
    return ImageBitmap.imageResource(R.drawable.prato)
}

@Composable
fun rememberPathTile(): ImageBitmap {
    return ImageBitmap.imageResource(R.drawable.terreno_centro)
}

// 1. Disegna solo il terreno (Livello 0)
fun DrawScope.drawTerrain(
    worldSize: Size,
    terrainTile: ImageBitmap,
    visibleRect: androidx.compose.ui.geometry.Rect
) {
    val tileSize = 128f

    val maxCols = ceil(worldSize.width / tileSize).toInt()
    val maxRows = ceil(worldSize.height / tileSize).toInt()

    val startCol = (visibleRect.left / tileSize).toInt() - 1
    val endCol = ceil(visibleRect.right / tileSize).toInt() + 1
    val startRow = (visibleRect.top / tileSize).toInt() - 1
    val endRow = ceil(visibleRect.bottom / tileSize).toInt() + 1

    for (row in startRow..endRow) {
        if (row < -1 || row > maxRows + 1) continue
        for (col in startCol..endCol) {
            if (col < -1 || col > maxCols + 1) continue

            drawImage(
                image = terrainTile,
                dstOffset = IntOffset((col * tileSize).toInt(), (row * tileSize).toInt()),
                dstSize = IntSize(tileSize.toInt(), tileSize.toInt())
            )
        }
    }
}

// 2. Disegna i sentieri che collegano le missioni (Livello 1)
fun DrawScope.drawPaths(
    points: List<Offset>,
    pathTile: ImageBitmap
) {
    if (points.size < 2) return

    val pathSpacing = 40f // Distanza tra una pietra e l'altra del sentiero

    for (i in 0 until points.size - 1) {
        val start = points[i]
        val end = points[i+1]

        val dist = hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()).toFloat()
        val steps = (dist / pathSpacing).toInt()

        val dx = (end.x - start.x) / steps
        val dy = (end.y - start.y) / steps

        for (j in 0..steps) {
            val px = start.x + dx * j
            val py = start.y + dy * j

            // Disegna il sentiero centrato
            drawImage(
                image = pathTile,
                dstOffset = IntOffset((px - pathTile.width / 2).toInt(), (py - pathTile.height / 2).toInt())
            )
        }
    }
}

// Generatore procedurale (SENZA Cartelli)
fun generateForestScenery(worldSize: Size, count: Int): List<SceneryObject> {
    val list = mutableListOf<SceneryObject>()
    val rnd = Random(9999)
    val center = androidx.compose.ui.geometry.Offset(worldSize.width / 2, worldSize.height / 2)

    for (i in 0 until count) {
        val x = rnd.nextFloat() * worldSize.width
        val y = rnd.nextFloat() * worldSize.height

        val distFromCenter = hypot((x - center.x).toDouble(), (y - center.y).toDouble())
        if (distFromCenter < 300.0) continue

        // NIENTE CARTELLI QUI, SOLO NATURA
        val type = when (rnd.nextInt(100)) {
            in 0..35 -> SceneryType.TREE_PINE
            in 36..60 -> SceneryType.TREE_OAK
            in 61..70 -> SceneryType.BUSH
            in 71..80 -> SceneryType.MUSHROOM_GROUP
            in 81..85 -> SceneryType.ROCK
            in 86..90 -> SceneryType.LOG
            else -> SceneryType.MUSHROOM_RED
        }

        val baseScale = when(type) {
            SceneryType.MUSHROOM_RED, SceneryType.MUSHROOM_GROUP -> 2.0f
            else -> 3.0f
        }
        val scale = baseScale + rnd.nextFloat() * 0.5f

        list.add(SceneryObject(i, type, x, y, scale, 0f))
    }
    return list
}