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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.onPointerEvent
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
private const val SCROLL_ZOOM_FACTOR = 0.05f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesignCanvas(
    state: ShipBuilderState,
    onSelectItem: (String) -> Unit,
    onDeselect: () -> Unit,
    onMoveItem: (String, Offset) -> Unit,
    onRotateItem: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rotateHandleScreenPos by remember { mutableStateOf<Offset?>(null) }
    var canvasState by remember { mutableStateOf(CanvasState()) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // Scroll-wheel zoom (Desktop). Uses a separate pointerInput scope
            // so scroll events don't interfere with the touch/drag gesture scope.
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.first()
                val newZoom = (canvasState.zoom * (1f - change.scrollDelta.y * SCROLL_ZOOM_FACTOR))
                    .coerceIn(CanvasState.MIN_ZOOM, CanvasState.MAX_ZOOM)
                canvasState = canvasState.copy(zoom = newZoom)
                change.consume()
            }
            // Touch/mouse gestures: tap, drag-item, pan, pinch-zoom.
            .pointerInput(state.selectedItemId) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position

                    val handlePos = rotateHandleScreenPos
                    val selectedId = state.selectedItemId
                    val hitRotateHandle = selectedId != null && handlePos != null &&
                            (downPos - handlePos).getDistance() < ROTATE_HANDLE_HIT_RADIUS * canvasState.zoom
                    val hitSelectedItem = selectedId != null && !hitRotateHandle &&
                            hitTestItem(downPos, selectedId, state, canvasState)

                    var totalDrag = Offset.Zero
                    var isDragging = false
                    var isMultiTouch = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val activeCount = event.changes.count { !it.changedToUp() }

                        // --- Multi-touch: pinch zoom + pan ---
                        if (activeCount >= 2) {
                            isMultiTouch = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newZoom = (canvasState.zoom * zoom)
                                .coerceIn(CanvasState.MIN_ZOOM, CanvasState.MAX_ZOOM)
                            canvasState = canvasState.copy(
                                offset = canvasState.offset + pan,
                                zoom = newZoom,
                            )
                            event.changes.forEach { it.consume() }
                            continue
                        }

                        // After multi-touch ends, consume remaining single-pointer
                        // events until release to prevent accidental taps/drags.
                        if (isMultiTouch) {
                            event.changes.forEach { it.consume() }
                            if (event.changes.all { it.changedToUp() }) break
                            continue
                        }

                        // --- Single pointer ---
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUp()) {
                            change.consume()
                            if (!isDragging) {
                                // Tap: hit-test for selection
                                val hitId = hitTestAllItems(downPos, state, canvasState)
                                if (hitId != null) onSelectItem(hitId) else onDeselect()
                            } else if (hitSelectedItem) {
                                // Item drag ended: snap to grid
                                val worldPos = getItemWorldPos(selectedId, state)
                                if (worldPos != null) {
                                    onMoveItem(selectedId, snapToGrid(worldPos, GRID_CELL_SIZE))
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
                                    val itemWorldPos = getItemWorldPos(selectedId, state)
                                    if (itemWorldPos != null) {
                                        val pointerWorld =
                                            canvasState.screenToWorld(change.position)
                                        onRotateItem(
                                            selectedId, atan2(
                                                pointerWorld.y - itemWorldPos.y,
                                                pointerWorld.x - itemWorldPos.x,
                                            )
                                        )
                                    }
                                }

                                hitSelectedItem -> {
                                    val worldDelta = dragDelta / canvasState.zoom
                                    val currentWorldPos = getItemWorldPos(selectedId, state)
                                    if (currentWorldPos != null) {
                                        onMoveItem(selectedId, currentWorldPos + worldDelta)
                                    }
                                }

                                else -> {
                                    // Pan
                                    canvasState =
                                        canvasState.copy(offset = canvasState.offset + dragDelta)
                                }
                            }
                        }
                    }
                }
            }
    ) {
        drawGrid(canvasState)
        rotateHandleScreenPos = drawPlacedItems(state, canvasState)
    }
}

private const val GRID_CELL_SIZE = 10f

private fun DrawScope.drawPlacedItems(state: ShipBuilderState, canvasState: CanvasState): Offset? {
    val selectedId = state.selectedItemId
    var rotateHandlePos: Offset? = null

    for (placed in state.placedHulls) {
        val hullDef = state.hullPieces.find { it.id == placed.hullPieceId } ?: continue
        val isSelected = placed.id == selectedId
        drawHullPiece(
            piece = placed,
            vertices = hullDef.vertices,
            isSelected = isSelected,
            canvasState = canvasState,
        )
        if (isSelected) {
            val worldPos = Offset(placed.position.x.raw, placed.position.y.raw)
            rotateHandlePos = drawRotateHandle(
                itemWorldPos = worldPos,
                itemRotation = placed.rotation.normalized,
                canvasState = canvasState,
            )
        }
    }

    for (placed in state.placedModules) {
        val isSelected = placed.id == selectedId
        drawModule(
            module = placed,
            isSelected = isSelected,
            isInvalid = placed.id in state.invalidPlacements,
            canvasState = canvasState,
        )
        if (isSelected) {
            val worldPos = Offset(placed.position.x.raw, placed.position.y.raw)
            rotateHandlePos = drawRotateHandle(
                itemWorldPos = worldPos,
                itemRotation = placed.rotation.normalized,
                canvasState = canvasState,
            )
        }
    }

    for (placed in state.placedTurrets) {
        val isSelected = placed.id == selectedId
        drawTurret(
            turret = placed,
            isSelected = isSelected,
            isInvalid = placed.id in state.invalidPlacements,
            canvasState = canvasState,
        )
        if (isSelected) {
            val worldPos = Offset(placed.position.x.raw, placed.position.y.raw)
            rotateHandlePos = drawRotateHandle(
                itemWorldPos = worldPos,
                itemRotation = placed.rotation.normalized,
                canvasState = canvasState,
            )
        }
    }

    return rotateHandlePos
}

private fun hitTestItem(
    screenPos: Offset,
    itemId: String,
    state: ShipBuilderState,
    canvasState: CanvasState,
): Boolean {
    val worldPos = canvasState.screenToWorld(screenPos)

    for (placed in state.placedHulls) {
        if (placed.id != itemId) continue
        val hullDef = state.hullPieces.find { it.id == placed.hullPieceId } ?: continue
        if (pointInHullPiece(worldPos, placed, hullDef.vertices)) return true
    }

    for (placed in state.placedModules) {
        if (placed.id != itemId) continue
        val itemPos = Offset(placed.position.x.raw, placed.position.y.raw)
        if ((worldPos - itemPos).getDistance() < HIT_RADIUS_MODULE) return true
    }

    for (placed in state.placedTurrets) {
        if (placed.id != itemId) continue
        val itemPos = Offset(placed.position.x.raw, placed.position.y.raw)
        if ((worldPos - itemPos).getDistance() < HIT_RADIUS_TURRET) return true
    }

    return false
}

private fun hitTestAllItems(
    screenPos: Offset,
    state: ShipBuilderState,
    canvasState: CanvasState,
): String? {
    val worldPos = canvasState.screenToWorld(screenPos)

    for (placed in state.placedTurrets.asReversed()) {
        val itemPos = Offset(placed.position.x.raw, placed.position.y.raw)
        if ((worldPos - itemPos).getDistance() < HIT_RADIUS_TURRET) return placed.id
    }

    for (placed in state.placedModules.asReversed()) {
        val itemPos = Offset(placed.position.x.raw, placed.position.y.raw)
        if ((worldPos - itemPos).getDistance() < HIT_RADIUS_MODULE) return placed.id
    }

    for (placed in state.placedHulls.asReversed()) {
        val hullDef = state.hullPieces.find { it.id == placed.hullPieceId } ?: continue
        if (pointInHullPiece(worldPos, placed, hullDef.vertices)) return placed.id
    }

    return null
}

private fun pointInHullPiece(
    worldPoint: Offset,
    placed: jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece,
    vertices: List<com.pandulapeter.kubriko.types.SceneOffset>,
): Boolean {
    if (vertices.size < 3) return false

    val rotation = placed.rotation.normalized
    val pos = Offset(placed.position.x.raw, placed.position.y.raw)

    val localX = worldPoint.x - pos.x
    val localY = worldPoint.y - pos.y
    val cosR = cos(-rotation)
    val sinR = sin(-rotation)
    val testX = localX * cosR - localY * sinR
    val testY = localX * sinR + localY * cosR

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
