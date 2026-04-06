package jez.lastfleetprotocol.prototype.components.shipbuilder.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.ShipBuilderState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val DRAG_THRESHOLD = 8f
private const val HIT_RADIUS_MODULE = 8f
private const val HIT_RADIUS_TURRET = 6f
private const val ROTATE_HANDLE_HIT_RADIUS = 12f

@Composable
fun DesignCanvas(
    state: ShipBuilderState,
    onPan: (Offset) -> Unit,
    onZoom: (Float) -> Unit,
    onSelectItem: (String) -> Unit,
    onDeselect: () -> Unit,
    onMoveItem: (String, Offset) -> Unit,
    onRotateItem: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Track the rotate handle screen position for hit testing
    var rotateHandleScreenPos by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(state.selectedItemId, state.canvasState) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position

                    // Check if we hit the rotate handle
                    val handlePos = rotateHandleScreenPos
                    val selectedId = state.selectedItemId
                    val hitRotateHandle = selectedId != null && handlePos != null &&
                            (downPos - handlePos).getDistance() < ROTATE_HANDLE_HIT_RADIUS * state.canvasState.zoom

                    // Check if we hit the selected item
                    val hitSelectedItem = selectedId != null && !hitRotateHandle &&
                            hitTestItem(downPos, selectedId, state)

                    var totalDrag = Offset.Zero
                    var isDragging = false
                    var isMultiTouch = false

                    // Process subsequent events
                    while (true) {
                        val event = awaitPointerEvent()

                        // Detect multi-touch (pinch)
                        val pointerCount = event.changes.count { !it.changedToUp() }
                        if (pointerCount >= 2) {
                            isMultiTouch = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f) onZoom(zoom)
                            if (pan != Offset.Zero) onPan(pan)
                            event.changes.forEach { it.consume() }
                            continue
                        }

                        if (isMultiTouch) {
                            // If we were in multi-touch mode, consume remaining events
                            if (event.type == PointerEventType.Release) {
                                event.changes.forEach { it.consume() }
                                break
                            }
                            event.changes.forEach { it.consume() }
                            continue
                        }

                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUp()) {
                            change.consume()
                            if (!isDragging) {
                                // It was a tap — do hit testing for selection
                                val hitId = hitTestAllItems(downPos, state)
                                if (hitId != null) {
                                    onSelectItem(hitId)
                                } else {
                                    onDeselect()
                                }
                            } else if (hitSelectedItem) {
                                // Drag ended on a moved item — snap position
                                val currentWorldPos = getItemWorldPos(selectedId, state)
                                if (currentWorldPos != null) {
                                    val snappedPos = snapToGrid(currentWorldPos, GRID_CELL_SIZE)
                                    onMoveItem(selectedId, snappedPos)
                                }
                            }
                            break
                        }

                        val dragDelta = change.positionChange()
                        totalDrag += dragDelta

                        if (!isDragging && totalDrag.getDistance() > DRAG_THRESHOLD) {
                            isDragging = true
                        }

                        if (isDragging) {
                            change.consume()
                            when {
                                hitRotateHandle -> {
                                    // Rotate mode: compute angle from item center to pointer
                                    val itemWorldPos = getItemWorldPos(selectedId, state)
                                    if (itemWorldPos != null) {
                                        val pointerWorld =
                                            state.canvasState.screenToWorld(change.position)
                                        val angle = atan2(
                                            pointerWorld.y - itemWorldPos.y,
                                            pointerWorld.x - itemWorldPos.x,
                                        )
                                        onRotateItem(selectedId, angle)
                                    }
                                }

                                hitSelectedItem -> {
                                    // Drag mode: move item by delta in world space
                                    val worldDelta = dragDelta / state.canvasState.zoom
                                    val currentWorldPos = getItemWorldPos(selectedId, state)
                                    if (currentWorldPos != null) {
                                        onMoveItem(selectedId, currentWorldPos + worldDelta)
                                    }
                                }

                                else -> {
                                    // Pan mode
                                    onPan(dragDelta)
                                }
                            }
                        }
                    }
                }
            }
    ) {
        drawGrid(state.canvasState)
        rotateHandleScreenPos = drawPlacedItems(state)
    }
}

private const val GRID_CELL_SIZE = 10f

/**
 * Draw all placed items, returning the screen position of the rotate handle if applicable.
 */
private fun DrawScope.drawPlacedItems(state: ShipBuilderState): Offset? {
    val selectedId = state.selectedItemId
    var rotateHandlePos: Offset? = null

    // Draw hull pieces
    for (placed in state.placedHulls) {
        val hullDef = state.hullPieces.find { it.id == placed.hullPieceId } ?: continue
        val isSelected = placed.id == selectedId
        drawHullPiece(
            piece = placed,
            vertices = hullDef.vertices,
            isSelected = isSelected,
            canvasState = state.canvasState,
        )
        if (isSelected) {
            val worldPos = Offset(placed.position.x.raw, placed.position.y.raw)
            rotateHandlePos = drawRotateHandle(
                itemWorldPos = worldPos,
                itemRotation = placed.rotation.normalized,
                canvasState = state.canvasState,
            )
        }
    }

    // Draw modules
    for (placed in state.placedModules) {
        val isSelected = placed.id == selectedId
        drawModule(
            module = placed,
            isSelected = isSelected,
            isInvalid = placed.id in state.invalidPlacements,
            canvasState = state.canvasState,
        )
        if (isSelected) {
            val worldPos = Offset(placed.position.x.raw, placed.position.y.raw)
            rotateHandlePos = drawRotateHandle(
                itemWorldPos = worldPos,
                itemRotation = placed.rotation.normalized,
                canvasState = state.canvasState,
            )
        }
    }

    // Draw turrets
    for (placed in state.placedTurrets) {
        val isSelected = placed.id == selectedId
        drawTurret(
            turret = placed,
            isSelected = isSelected,
            isInvalid = placed.id in state.invalidPlacements,
            canvasState = state.canvasState,
        )
        if (isSelected) {
            val worldPos = Offset(placed.position.x.raw, placed.position.y.raw)
            rotateHandlePos = drawRotateHandle(
                itemWorldPos = worldPos,
                itemRotation = placed.rotation.normalized,
                canvasState = state.canvasState,
            )
        }
    }

    return rotateHandlePos
}

/**
 * Hit test a specific item by ID. Returns true if the screen position hits it.
 */
private fun hitTestItem(screenPos: Offset, itemId: String, state: ShipBuilderState): Boolean {
    val canvas = state.canvasState
    val worldPos = canvas.screenToWorld(screenPos)

    // Check hull pieces
    for (placed in state.placedHulls) {
        if (placed.id != itemId) continue
        val hullDef = state.hullPieces.find { it.id == placed.hullPieceId } ?: continue
        if (pointInHullPiece(worldPos, placed, hullDef.vertices)) return true
    }

    // Check modules
    for (placed in state.placedModules) {
        if (placed.id != itemId) continue
        val itemPos = Offset(placed.position.x.raw, placed.position.y.raw)
        if ((worldPos - itemPos).getDistance() < HIT_RADIUS_MODULE) return true
    }

    // Check turrets
    for (placed in state.placedTurrets) {
        if (placed.id != itemId) continue
        val itemPos = Offset(placed.position.x.raw, placed.position.y.raw)
        if ((worldPos - itemPos).getDistance() < HIT_RADIUS_TURRET) return true
    }

    return false
}

/**
 * Hit test all items at a screen position, iterating in reverse order (topmost first).
 * Returns the ID of the hit item, or null.
 */
private fun hitTestAllItems(screenPos: Offset, state: ShipBuilderState): String? {
    val canvas = state.canvasState
    val worldPos = canvas.screenToWorld(screenPos)

    // Check turrets last drawn = topmost, so check in reverse
    for (placed in state.placedTurrets.asReversed()) {
        val itemPos = Offset(placed.position.x.raw, placed.position.y.raw)
        if ((worldPos - itemPos).getDistance() < HIT_RADIUS_TURRET) return placed.id
    }

    // Check modules
    for (placed in state.placedModules.asReversed()) {
        val itemPos = Offset(placed.position.x.raw, placed.position.y.raw)
        if ((worldPos - itemPos).getDistance() < HIT_RADIUS_MODULE) return placed.id
    }

    // Check hull pieces
    for (placed in state.placedHulls.asReversed()) {
        val hullDef = state.hullPieces.find { it.id == placed.hullPieceId } ?: continue
        if (pointInHullPiece(worldPos, placed, hullDef.vertices)) return placed.id
    }

    return null
}

/**
 * Point-in-polygon test for a hull piece, accounting for position and rotation.
 * Uses the ray casting algorithm.
 */
private fun pointInHullPiece(
    worldPoint: Offset,
    placed: jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece,
    vertices: List<com.pandulapeter.kubriko.types.SceneOffset>,
): Boolean {
    if (vertices.size < 3) return false

    val rotation = placed.rotation.normalized
    val pos = Offset(placed.position.x.raw, placed.position.y.raw)

    // Transform the test point into hull local space
    val localX = worldPoint.x - pos.x
    val localY = worldPoint.y - pos.y
    // Inverse rotation
    val cosR = cos(-rotation)
    val sinR = sin(-rotation)
    val testX = localX * cosR - localY * sinR
    val testY = localX * sinR + localY * cosR

    // Ray casting algorithm against hull vertices (in local space)
    var inside = false
    var j = vertices.size - 1
    for (i in vertices.indices) {
        val xi = vertices[i].x.raw
        val yi = vertices[i].y.raw
        val xj = vertices[j].x.raw
        val yj = vertices[j].y.raw

        val intersect = ((yi > testY) != (yj > testY)) &&
                (testX < (xj - xi) * (testY - yi) / (yj - yi) + xi)

        if (intersect) inside = !inside
        j = i
    }

    return inside
}

/**
 * Get the world-space position of an item by its ID.
 */
private fun getItemWorldPos(itemId: String, state: ShipBuilderState): Offset? {
    for (placed in state.placedHulls) {
        if (placed.id == itemId) return Offset(placed.position.x.raw, placed.position.y.raw)
    }
    for (placed in state.placedModules) {
        if (placed.id == itemId) return Offset(placed.position.x.raw, placed.position.y.raw)
    }
    for (placed in state.placedTurrets) {
        if (placed.id == itemId) return Offset(placed.position.x.raw, placed.position.y.raw)
    }
    return null
}
