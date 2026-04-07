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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.CanvasState.Companion.DRAG_THRESHOLD
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.CanvasState.Companion.SCROLL_ZOOM_FACTOR
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.ShipBuilderState


/**
 * Callback interface for raw canvas pointer events.
 * The canvas reports what happened; the VM decides what it means.
 * All positions are in world coordinates (accounting for pan/zoom).
 */
interface CanvasInputHandler {
    /** Single tap (press + release within drag threshold) at a world position. */
    fun onTap(worldPosition: Offset)

    /** Drag started at a world position. Return true if consumed (e.g. item hit), false to pan. */
    fun onDragStart(worldPosition: Offset): Boolean

    /** Drag moved to a new world position with a world-space delta. Return true if consumed, false to pan. */
    fun onDragMove(worldPosition: Offset, worldDelta: Offset): Boolean

    /** Drag ended at a world position. */
    fun onDragEnd(worldPosition: Offset)
}

@Composable
fun DesignCanvas(
    state: ShipBuilderState,
    inputHandler: CanvasInputHandler,
    modifier: Modifier = Modifier,
) {
    var canvasState by remember { mutableStateOf(CanvasState()) }
    val inputHandlerRef = rememberUpdatedState(inputHandler)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            // Scroll-wheel zoom. Uses awaitPointerEventScope to detect scroll
            // events cross-platform (onPointerEvent is Desktop-only).
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.firstOrNull() ?: continue
                            val newZoom =
                                (canvasState.zoom * (1f - change.scrollDelta.y * SCROLL_ZOOM_FACTOR))
                                    .coerceIn(CanvasState.MIN_ZOOM, CanvasState.MAX_ZOOM)
                            canvasState = canvasState.copy(zoom = newZoom)
                            change.consume()
                        }
                    }
                }
            }
            // Touch/mouse gestures: tap, drag, pan, pinch-zoom.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position

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

                        if (isMultiTouch) {
                            event.changes.forEach { it.consume() }
                            if (event.changes.all { it.changedToUp() }) break
                            continue
                        }

                        // --- Single pointer ---
                        val change = event.changes.firstOrNull() ?: break
                        if (change.changedToUp()) {
                            change.consume()
                            if (isDragging) {
                                inputHandlerRef.value.onDragEnd(
                                    canvasState.screenToWorld(change.position)
                                )
                            } else {
                                inputHandlerRef.value.onTap(
                                    canvasState.screenToWorld(downPos)
                                )
                            }
                            break
                        }

                        val dragDelta = change.positionChange()
                        totalDrag += dragDelta

                        if (!isDragging && totalDrag.getDistance() > DRAG_THRESHOLD) {
                            isDragging = true
                            val consumed = inputHandlerRef.value.onDragStart(
                                canvasState.screenToWorld(downPos)
                            )
                            if (!consumed) {
                                canvasState = canvasState.copy(
                                    offset = canvasState.offset + totalDrag
                                )
                            }
                        }

                        if (isDragging) {
                            change.consume()
                            val consumed = inputHandlerRef.value.onDragMove(
                                worldPosition = canvasState.screenToWorld(change.position),
                                worldDelta = dragDelta / canvasState.zoom,
                            )
                            if (!consumed) {
                                canvasState = canvasState.copy(
                                    offset = canvasState.offset + dragDelta
                                )
                            }
                        }
                    }
                }
            }
    ) {
        drawGrid(canvasState)
        drawPlacedItems(state, canvasState)
    }
}

private fun DrawScope.drawPlacedItems(state: ShipBuilderState, canvasState: CanvasState) {
    val selectedId = state.selectedItemId

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
            drawRotateHandle(
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
            drawRotateHandle(
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
            drawRotateHandle(
                itemWorldPos = worldPos,
                itemRotation = placed.rotation.normalized,
                canvasState = canvasState,
            )
        }
    }
}
