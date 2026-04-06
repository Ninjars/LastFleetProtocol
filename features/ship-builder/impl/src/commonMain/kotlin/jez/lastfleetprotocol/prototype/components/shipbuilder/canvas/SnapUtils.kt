package jez.lastfleetprotocol.prototype.components.shipbuilder.canvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

/**
 * Snaps a position to the nearest grid intersection.
 * Each coordinate is rounded to the nearest multiple of [cellSize].
 */
fun snapToGrid(position: Offset, cellSize: Float): Offset =
    Offset(
        x = (position.x / cellSize).roundToInt() * cellSize,
        y = (position.y / cellSize).roundToInt() * cellSize,
    )
