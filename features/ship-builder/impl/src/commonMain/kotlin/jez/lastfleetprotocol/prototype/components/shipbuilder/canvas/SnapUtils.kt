package jez.lastfleetprotocol.prototype.components.shipbuilder.canvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

/**
 * Snaps a position to the nearest grid centre. Used for placing modules
 * Each coordinate is rounded to the nearest multiple of [cellSize].
 */
fun snapToGridCentre(position: Offset, cellSize: Float): Offset {
    val xOffset = (if (position.x < 0) -cellSize else cellSize) / 2f
    val yOffset = (if (position.y < 0) -cellSize else cellSize) / 2f
    return Offset(
        x = (position.x / cellSize).toInt() * cellSize + xOffset,
        y = (position.y / cellSize).toInt() * cellSize + yOffset,
    )
}

/**
 * Snaps a position to the nearest grid intersection. Used for placing vertexes.
 * Each coordinate is rounded to the nearest multiple of [cellSize].
 */
fun snapToGridCorner(position: Offset, cellSize: Float): Offset =
    Offset(
        x = (position.x / cellSize).roundToInt() * cellSize,
        y = (position.y / cellSize).roundToInt() * cellSize,
    )
