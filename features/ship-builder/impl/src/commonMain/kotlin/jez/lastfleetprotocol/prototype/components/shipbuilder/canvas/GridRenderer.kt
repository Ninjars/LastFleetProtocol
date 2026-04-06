package jez.lastfleetprotocol.prototype.components.shipbuilder.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Draws a grid in world space, transformed by the canvas state.
 * Grid lines are thin grey; origin crosshair lines are slightly brighter.
 */
fun DrawScope.drawGrid(canvasState: CanvasState, gridCellSize: Float = 10f) {
    val scaledCellSize = gridCellSize * canvasState.zoom
    if (scaledCellSize < 2f) return // Don't draw if cells are too small to see

    // Calculate visible world-space bounds
    val topLeft = canvasState.screenToWorld(Offset.Zero)
    val bottomRight = canvasState.screenToWorld(Offset(size.width, size.height))

    val minX = floor(topLeft.x / gridCellSize) * gridCellSize
    val maxX = ceil(bottomRight.x / gridCellSize) * gridCellSize
    val minY = floor(topLeft.y / gridCellSize) * gridCellSize
    val maxY = ceil(bottomRight.y / gridCellSize) * gridCellSize

    val gridColor = Color.Gray.copy(alpha = 0.3f)
    val originColor = Color.Gray.copy(alpha = 0.6f)

    // Vertical grid lines
    var x = minX
    while (x <= maxX) {
        val screenX = canvasState.worldToScreen(Offset(x, 0f)).x
        val isOrigin = x == 0f
        drawLine(
            color = if (isOrigin) originColor else gridColor,
            start = Offset(screenX, 0f),
            end = Offset(screenX, size.height),
            strokeWidth = if (isOrigin) 1.5f else 0.5f,
        )
        x += gridCellSize
    }

    // Horizontal grid lines
    var y = minY
    while (y <= maxY) {
        val screenY = canvasState.worldToScreen(Offset(0f, y)).y
        val isOrigin = y == 0f
        drawLine(
            color = if (isOrigin) originColor else gridColor,
            start = Offset(0f, screenY),
            end = Offset(size.width, screenY),
            strokeWidth = if (isOrigin) 1.5f else 0.5f,
        )
        y += gridCellSize
    }
}
