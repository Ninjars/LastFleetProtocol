package jez.lastfleetprotocol.prototype.components.shipbuilder.canvas

import androidx.compose.ui.geometry.Offset

/**
 * Snaps a position to the nearest grid intersection.
 * Each coordinate is rounded to the nearest multiple of [cellSize].
 */
fun snapToGrid(position: Offset, cellSize: Float): Offset {
    val xOffset = (if (position.x < 0) -cellSize else cellSize) / 2f
    val yOffset = (if (position.y < 0) -cellSize else cellSize) / 2f
    return Offset(
        x = (position.x / cellSize).toInt() * cellSize + xOffset,
        y = (position.y / cellSize).toInt() * cellSize + yOffset,
    )
}
