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
    alpha: Float = 1f,
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
        color = Color.Cyan.copy(alpha = 0.2f * alpha),
    )
    drawPath(
        path = path,
        color = if (isSelected) Color.Cyan.copy(alpha = alpha) else Color.Cyan.copy(alpha = 0.6f * alpha),
        style = Stroke(width = if (isSelected) 3f else 1.5f),
    )
}

/**
 * Draw a module as a yellow square on the canvas.
 * If [isSelected], uses a brighter color and thicker stroke.
 * If [isInvalid], renders with a red tint to indicate placement outside hull bounds.
 */
fun DrawScope.drawModule(
    module: PlacedModule,
    isSelected: Boolean,
    isInvalid: Boolean = false,
    canvasState: CanvasState,
    alpha: Float = 1f,
) {
    val pos = Offset(module.position.x.raw, module.position.y.raw)
    val screenPos = canvasState.worldToScreen(pos)
    val halfSize = MODULE_HALF_SIZE * canvasState.zoom

    val baseColor = if (isInvalid) Color.Red else Color.Yellow
    val fillAlpha = (if (isSelected) 0.6f else 0.4f) * alpha
    val strokeColor = if (isSelected) baseColor.copy(alpha = alpha) else baseColor.copy(alpha = 0.7f * alpha)
    val strokeWidth = if (isSelected) 2f else 1f

    drawRect(
        color = baseColor.copy(alpha = fillAlpha),
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
 * Draw a turret as a circle with a direction indicator on the canvas.
 * If [isSelected], uses a brighter color and thicker stroke.
 * If [isInvalid], renders with a magenta tint to indicate placement outside hull bounds.
 */
fun DrawScope.drawTurret(
    turret: PlacedTurret,
    isSelected: Boolean,
    isInvalid: Boolean = false,
    canvasState: CanvasState,
    alpha: Float = 1f,
) {
    val pos = Offset(turret.position.x.raw, turret.position.y.raw)
    val screenPos = canvasState.worldToScreen(pos)
    val radius = TURRET_RADIUS * canvasState.zoom

    val baseColor = if (isInvalid) Color.Magenta else Color.Red
    val fillAlpha = (if (isSelected) 0.6f else 0.4f) * alpha
    val strokeColor = if (isSelected) baseColor.copy(alpha = alpha) else baseColor.copy(alpha = 0.7f * alpha)
    val strokeWidth = if (isSelected) 2f else 1f

    drawCircle(
        color = baseColor.copy(alpha = fillAlpha),
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

private const val VERTEX_HANDLE_RADIUS = 5f
private const val SELECTED_VERTEX_HANDLE_RADIUS = 7f

/**
 * Draw the in-progress creation polygon: edges, closing line, and vertex handles.
 * Red edges when concave, green when convex.
 * Selected vertex is drawn larger and white; others are cyan outlines.
 */
fun DrawScope.drawCreationPolygon(
    vertices: List<Offset>,
    selectedVertexIndex: Int?,
    isConvex: Boolean,
    canvasState: CanvasState,
) {
    if (vertices.isEmpty()) return

    val edgeColor = if (isConvex) Color.Green else Color.Red

    // Draw edges between consecutive vertices (and closing edge)
    if (vertices.size >= 2) {
        val path = Path()
        val firstScreen = canvasState.worldToScreen(vertices[0])
        path.moveTo(firstScreen.x, firstScreen.y)
        for (i in 1 until vertices.size) {
            val screen = canvasState.worldToScreen(vertices[i])
            path.lineTo(screen.x, screen.y)
        }
        // Close the polygon (line from last back to first)
        path.close()

        // Semi-transparent fill
        drawPath(
            path = path,
            color = edgeColor.copy(alpha = 0.1f),
        )
        // Edge outline
        drawPath(
            path = path,
            color = edgeColor.copy(alpha = 0.8f),
            style = Stroke(width = 2f),
        )
    } else {
        // Single vertex — just draw the handle below
    }

    // Draw vertex handles
    for (i in vertices.indices) {
        val screenPos = canvasState.worldToScreen(vertices[i])
        if (i == selectedVertexIndex) {
            // Selected vertex: larger, filled white
            drawCircle(
                color = Color.White,
                radius = SELECTED_VERTEX_HANDLE_RADIUS * canvasState.zoom.coerceIn(0.5f, 2f),
                center = screenPos,
            )
        } else {
            // Other vertices: cyan outline
            val radius = VERTEX_HANDLE_RADIUS * canvasState.zoom.coerceIn(0.5f, 2f)
            drawCircle(
                color = Color.Cyan.copy(alpha = 0.3f),
                radius = radius,
                center = screenPos,
            )
            drawCircle(
                color = Color.Cyan,
                radius = radius,
                center = screenPos,
                style = Stroke(width = 1.5f),
            )
        }
    }
}
