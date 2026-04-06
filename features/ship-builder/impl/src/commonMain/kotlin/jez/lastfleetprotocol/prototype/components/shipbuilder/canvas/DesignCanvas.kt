package jez.lastfleetprotocol.prototype.components.shipbuilder.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.ShipBuilderState
import kotlin.math.cos
import kotlin.math.sin

private const val MODULE_HALF_SIZE = 5f
private const val TURRET_RADIUS = 4f

@Composable
fun DesignCanvas(
    state: ShipBuilderState,
    onPan: (Offset) -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                    onPan(pan)
                    onZoom(zoom)
                }
            }
    ) {
        drawGrid(state.canvasState)
        drawPlacedItems(state)
    }
}

private fun DrawScope.drawPlacedItems(state: ShipBuilderState) {
    val canvas = state.canvasState

    // Draw hull pieces as outlined convex polygons
    for (placed in state.placedHulls) {
        val hullDef = state.hullPieces.find { it.id == placed.hullPieceId } ?: continue
        val rotation = placed.rotation.normalized
        val pos = Offset(placed.position.x.raw, placed.position.y.raw)

        val path = Path()
        val vertices = hullDef.vertices
        if (vertices.isEmpty()) continue

        for (i in vertices.indices) {
            val vx = vertices[i].x.raw
            val vy = vertices[i].y.raw
            // Rotate vertex around origin
            val rx = vx * cos(rotation) - vy * sin(rotation)
            val ry = vx * sin(rotation) + vy * cos(rotation)
            val screenPoint = canvas.worldToScreen(Offset(pos.x + rx, pos.y + ry))
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
            color = Color.Cyan,
            style = Stroke(width = 1.5f),
        )
    }

    // Draw modules as small squares
    for (placed in state.placedModules) {
        val pos = Offset(placed.position.x.raw, placed.position.y.raw)
        val screenPos = canvas.worldToScreen(pos)
        val halfSize = MODULE_HALF_SIZE * canvas.zoom

        drawRect(
            color = Color.Yellow.copy(alpha = 0.4f),
            topLeft = Offset(screenPos.x - halfSize, screenPos.y - halfSize),
            size = androidx.compose.ui.geometry.Size(halfSize * 2, halfSize * 2),
        )
        drawRect(
            color = Color.Yellow,
            topLeft = Offset(screenPos.x - halfSize, screenPos.y - halfSize),
            size = androidx.compose.ui.geometry.Size(halfSize * 2, halfSize * 2),
            style = Stroke(width = 1f),
        )
    }

    // Draw turrets as small circles
    for (placed in state.placedTurrets) {
        val pos = Offset(placed.position.x.raw, placed.position.y.raw)
        val screenPos = canvas.worldToScreen(pos)
        val radius = TURRET_RADIUS * canvas.zoom

        drawCircle(
            color = Color.Red.copy(alpha = 0.4f),
            radius = radius,
            center = screenPos,
        )
        drawCircle(
            color = Color.Red,
            radius = radius,
            center = screenPos,
            style = Stroke(width = 1f),
        )
    }
}
