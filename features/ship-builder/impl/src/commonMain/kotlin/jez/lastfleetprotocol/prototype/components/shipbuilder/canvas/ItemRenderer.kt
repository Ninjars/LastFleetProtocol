package jez.lastfleetprotocol.prototype.components.shipbuilder.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedModule
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedTurret
import kotlin.math.cos
import kotlin.math.sin

private const val MODULE_HALF_SIZE = 5f
private const val TURRET_RADIUS = 4f
private const val ROTATE_HANDLE_OFFSET = 20f
private const val ROTATE_HANDLE_RADIUS = 4f

/**
 * Draw a hull piece polygon outline on the canvas.
 * If [isSelected], uses a bright cyan outline with thicker stroke.
 */
fun DrawScope.drawHullPiece(
    piece: PlacedHullPiece,
    vertices: List<SceneOffset>,
    isSelected: Boolean,
    canvasState: CanvasState,
) {
    if (vertices.isEmpty()) return

    val rotation = piece.rotation.normalized
    val pos = Offset(piece.position.x.raw, piece.position.y.raw)

    val path = Path()
    for (i in vertices.indices) {
        val vx = vertices[i].x.raw
        val vy = vertices[i].y.raw
        val rx = vx * cos(rotation) - vy * sin(rotation)
        val ry = vx * sin(rotation) + vy * cos(rotation)
        val screenPoint = canvasState.worldToScreen(Offset(pos.x + rx, pos.y + ry))
        if (i == 0) {
            path.moveTo(screenPoint.x, screenPoint.y)
        } else {
            path.lineTo(screenPoint.x, screenPoint.y)
        }
    }
    path.close()

    drawPath(
        path = path,
        color = Color.Cyan.copy(alpha = 0.2f),
    )
    drawPath(
        path = path,
        color = if (isSelected) Color.Cyan else Color.Cyan.copy(alpha = 0.6f),
        style = Stroke(width = if (isSelected) 3f else 1.5f),
    )
}

/**
 * Draw a module as a yellow square on the canvas.
 * If [isSelected], uses a brighter color and thicker stroke.
 */
fun DrawScope.drawModule(
    module: PlacedModule,
    isSelected: Boolean,
    canvasState: CanvasState,
) {
    val pos = Offset(module.position.x.raw, module.position.y.raw)
    val screenPos = canvasState.worldToScreen(pos)
    val halfSize = MODULE_HALF_SIZE * canvasState.zoom

    val fillAlpha = if (isSelected) 0.6f else 0.4f
    val strokeColor = if (isSelected) Color.Yellow else Color.Yellow.copy(alpha = 0.7f)
    val strokeWidth = if (isSelected) 2f else 1f

    drawRect(
        color = Color.Yellow.copy(alpha = fillAlpha),
        topLeft = Offset(screenPos.x - halfSize, screenPos.y - halfSize),
        size = Size(halfSize * 2, halfSize * 2),
    )
    drawRect(
        color = strokeColor,
        topLeft = Offset(screenPos.x - halfSize, screenPos.y - halfSize),
        size = Size(halfSize * 2, halfSize * 2),
        style = Stroke(width = strokeWidth),
    )
}

/**
 * Draw a turret as a red circle with a direction indicator on the canvas.
 * If [isSelected], uses a brighter color and thicker stroke.
 */
fun DrawScope.drawTurret(
    turret: PlacedTurret,
    isSelected: Boolean,
    canvasState: CanvasState,
) {
    val pos = Offset(turret.position.x.raw, turret.position.y.raw)
    val screenPos = canvasState.worldToScreen(pos)
    val radius = TURRET_RADIUS * canvasState.zoom

    val fillAlpha = if (isSelected) 0.6f else 0.4f
    val strokeColor = if (isSelected) Color.Red else Color.Red.copy(alpha = 0.7f)
    val strokeWidth = if (isSelected) 2f else 1f

    drawCircle(
        color = Color.Red.copy(alpha = fillAlpha),
        radius = radius,
        center = screenPos,
    )
    drawCircle(
        color = strokeColor,
        radius = radius,
        center = screenPos,
        style = Stroke(width = strokeWidth),
    )

    // Direction indicator line
    val rotation = turret.rotation.normalized
    val dirEnd = Offset(
        screenPos.x + cos(rotation) * radius * 1.5f,
        screenPos.y + sin(rotation) * radius * 1.5f,
    )
    drawLine(
        color = strokeColor,
        start = screenPos,
        end = dirEnd,
        strokeWidth = strokeWidth,
    )
}

/**
 * Draw the free-rotate handle for a selected item.
 * Returns the screen-space center of the handle for hit testing.
 */
fun DrawScope.drawRotateHandle(
    itemWorldPos: Offset,
    itemRotation: Float,
    canvasState: CanvasState,
): Offset {
    val handleWorldX = itemWorldPos.x + cos(itemRotation) * ROTATE_HANDLE_OFFSET
    val handleWorldY = itemWorldPos.y + sin(itemRotation) * ROTATE_HANDLE_OFFSET
    val handleScreen = canvasState.worldToScreen(Offset(handleWorldX, handleWorldY))
    val handleRadius = ROTATE_HANDLE_RADIUS * canvasState.zoom

    drawCircle(
        color = Color.White.copy(alpha = 0.3f),
        radius = handleRadius,
        center = handleScreen,
    )
    drawCircle(
        color = Color.White,
        radius = handleRadius,
        center = handleScreen,
        style = Stroke(width = 1.5f),
    )

    // Draw line from item center to handle
    val itemScreen = canvasState.worldToScreen(itemWorldPos)
    drawLine(
        color = Color.White.copy(alpha = 0.5f),
        start = itemScreen,
        end = handleScreen,
        strokeWidth = 1f,
    )

    return handleScreen
}
