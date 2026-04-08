package jez.lastfleetprotocol.prototype.components.shipbuilder.canvas

import androidx.compose.ui.geometry.Offset
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

private const val ROTATE_HANDLE_OFFSET = 20f
private const val ROTATE_HANDLE_RADIUS = 4f

/**
 * Build a [Path] from a polygon's vertices, applying position, rotation, and mirror
 * transforms, then converting to screen space.
 */
private fun buildPolygonPath(
    vertices: List<SceneOffset>,
    posX: Float,
    posY: Float,
    rotation: Float,
    mirrorX: Boolean,
    mirrorY: Boolean,
    canvasState: CanvasState,
): Path {
    val mx = if (mirrorX) -1f else 1f
    val my = if (mirrorY) -1f else 1f
    val cosR = cos(rotation)
    val sinR = sin(rotation)

    val path = Path()
    for (i in vertices.indices) {
        val vx = vertices[i].x.raw * my
        val vy = vertices[i].y.raw * mx
        val rx = vx * cosR - vy * sinR
        val ry = vx * sinR + vy * cosR
        val screen = canvasState.worldToScreen(Offset(posX + rx, posY + ry))
        if (i == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
    }
    path.close()
    return path
}

/**
 * Draw a hull piece polygon outline on the canvas.
 */
fun DrawScope.drawHullPiece(
    piece: PlacedHullPiece,
    vertices: List<SceneOffset>,
    isSelected: Boolean,
    canvasState: CanvasState,
    alpha: Float = 1f,
) {
    if (vertices.isEmpty()) return
    val path = buildPolygonPath(
        vertices, piece.position.x.raw, piece.position.y.raw,
        piece.rotation.normalized, piece.mirrorX, piece.mirrorY, canvasState,
    )
    drawPath(path, Color.Cyan.copy(alpha = 0.2f * alpha))
    drawPath(
        path, if (isSelected) Color.Cyan.copy(alpha = alpha) else Color.Cyan.copy(alpha = 0.6f * alpha),
        style = Stroke(width = if (isSelected) 3f else 1.5f),
    )
}

/**
 * Draw a module on the canvas using its polygon vertices.
 * If [isInvalid], renders with a red tint to indicate placement outside hull bounds.
 */
fun DrawScope.drawModule(
    module: PlacedModule,
    vertices: List<SceneOffset>,
    isSelected: Boolean,
    isInvalid: Boolean = false,
    canvasState: CanvasState,
    alpha: Float = 1f,
) {
    val baseColor = if (isInvalid) Color.Red else Color.Yellow
    val fillAlpha = (if (isSelected) 0.6f else 0.4f) * alpha
    val strokeColor = if (isSelected) baseColor.copy(alpha = alpha) else baseColor.copy(alpha = 0.7f * alpha)
    val strokeWidth = if (isSelected) 2f else 1f

    if (vertices.isNotEmpty()) {
        val path = buildPolygonPath(
            vertices, module.position.x.raw, module.position.y.raw,
            module.rotation.normalized, module.mirrorX, module.mirrorY, canvasState,
        )
        drawPath(path, baseColor.copy(alpha = fillAlpha))
        drawPath(path, strokeColor, style = Stroke(width = strokeWidth))
    } else {
        // Fallback: simple square for definitions without vertices
        val screenPos = canvasState.worldToScreen(Offset(module.position.x.raw, module.position.y.raw))
        val halfSize = 5f * canvasState.zoom
        drawRect(baseColor.copy(alpha = fillAlpha), Offset(screenPos.x - halfSize, screenPos.y - halfSize), androidx.compose.ui.geometry.Size(halfSize * 2, halfSize * 2))
        drawRect(strokeColor, Offset(screenPos.x - halfSize, screenPos.y - halfSize), androidx.compose.ui.geometry.Size(halfSize * 2, halfSize * 2), style = Stroke(width = strokeWidth))
    }
}

/**
 * Draw a turret on the canvas using its polygon vertices, with a direction indicator.
 * If [isInvalid], renders with a magenta tint.
 */
fun DrawScope.drawTurret(
    turret: PlacedTurret,
    vertices: List<SceneOffset>,
    isSelected: Boolean,
    isInvalid: Boolean = false,
    canvasState: CanvasState,
    alpha: Float = 1f,
) {
    val baseColor = if (isInvalid) Color.Magenta else Color.Red
    val fillAlpha = (if (isSelected) 0.6f else 0.4f) * alpha
    val strokeColor = if (isSelected) baseColor.copy(alpha = alpha) else baseColor.copy(alpha = 0.7f * alpha)
    val strokeWidth = if (isSelected) 2f else 1f

    val pos = Offset(turret.position.x.raw, turret.position.y.raw)
    val screenPos = canvasState.worldToScreen(pos)

    if (vertices.isNotEmpty()) {
        val path = buildPolygonPath(
            vertices, pos.x, pos.y,
            turret.rotation.normalized, turret.mirrorX, turret.mirrorY, canvasState,
        )
        drawPath(path, baseColor.copy(alpha = fillAlpha))
        drawPath(path, strokeColor, style = Stroke(width = strokeWidth))
    } else {
        // Fallback: simple circle
        val radius = 4f * canvasState.zoom
        drawCircle(baseColor.copy(alpha = fillAlpha), radius, screenPos)
        drawCircle(strokeColor, radius, screenPos, style = Stroke(width = strokeWidth))
    }

    // Direction indicator line (apply mirror to facing direction)
    val rotation = turret.rotation.normalized
    val dirX = if (turret.mirrorY) -1f else 1f
    val dirY = if (turret.mirrorX) -1f else 1f
    val indicatorLen = 8f * canvasState.zoom
    val dirEnd = Offset(
        screenPos.x + (cos(rotation) * dirX) * indicatorLen,
        screenPos.y + (sin(rotation) * dirY) * indicatorLen,
    )
    drawLine(strokeColor, screenPos, dirEnd, strokeWidth)
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

    drawCircle(Color.White.copy(alpha = 0.3f), handleRadius, handleScreen)
    drawCircle(Color.White, handleRadius, handleScreen, style = Stroke(width = 1.5f))

    val itemScreen = canvasState.worldToScreen(itemWorldPos)
    drawLine(Color.White.copy(alpha = 0.5f), itemScreen, handleScreen, 1f)

    return handleScreen
}

private const val VERTEX_HANDLE_RADIUS = 5f
private const val SELECTED_VERTEX_HANDLE_RADIUS = 7f

/**
 * Draw the in-progress creation polygon: edges, closing line, and vertex handles.
 * Red edges when concave, green when convex.
 */
fun DrawScope.drawCreationPolygon(
    vertices: List<Offset>,
    selectedVertexIndex: Int?,
    isConvex: Boolean,
    canvasState: CanvasState,
) {
    if (vertices.isEmpty()) return

    val edgeColor = if (isConvex) Color.Green else Color.Red

    if (vertices.size >= 2) {
        val path = Path()
        val firstScreen = canvasState.worldToScreen(vertices[0])
        path.moveTo(firstScreen.x, firstScreen.y)
        for (i in 1 until vertices.size) {
            val screen = canvasState.worldToScreen(vertices[i])
            path.lineTo(screen.x, screen.y)
        }
        path.close()
        drawPath(path, edgeColor.copy(alpha = 0.1f))
        drawPath(path, edgeColor.copy(alpha = 0.8f), style = Stroke(width = 2f))
    }

    for (i in vertices.indices) {
        val screenPos = canvasState.worldToScreen(vertices[i])
        if (i == selectedVertexIndex) {
            drawCircle(
                Color.White,
                SELECTED_VERTEX_HANDLE_RADIUS * canvasState.zoom.coerceIn(0.5f, 2f),
                screenPos,
            )
        } else {
            val radius = VERTEX_HANDLE_RADIUS * canvasState.zoom.coerceIn(0.5f, 2f)
            drawCircle(Color.Cyan.copy(alpha = 0.3f), radius, screenPos)
            drawCircle(Color.Cyan, radius, screenPos, style = Stroke(width = 1.5f))
        }
    }
}
