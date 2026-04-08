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
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.CanvasState.Companion.DRAG_THRESHOLD
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.CanvasState.Companion.SCROLL_ZOOM_FACTOR
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.EditorMode
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderIntent
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderState

/**
 * Callback for canvas pointer intents. Returns true if the intent was
 * consumed by item interaction (move, rotate, vertex drag), false if
 * the canvas should pan instead.
 */
private typealias CanvasIntentHandler = (ShipBuilderIntent) -> Boolean

@Composable
fun DesignCanvas(
    state: ShipBuilderState,
    onIntent: CanvasIntentHandler,
    modifier: Modifier = Modifier,
) {
    var canvasState by remember { mutableStateOf(CanvasState()) }
    val onIntentRef by rememberUpdatedState(onIntent)

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
                                onIntentRef(
                                    ShipBuilderIntent.CanvasDragEnd(
                                        canvasState.screenToWorld(change.position)
                                    )
                                )
                            } else {
                                onIntentRef(
                                    ShipBuilderIntent.CanvasTap(
                                        canvasState.screenToWorld(downPos)
                                    )
                                )
                            }
                            break
                        }

                        val dragDelta = change.positionChange()
                        totalDrag += dragDelta

                        if (!isDragging && totalDrag.getDistance() > DRAG_THRESHOLD) {
                            isDragging = true
                            val consumed = onIntentRef(
                                ShipBuilderIntent.CanvasDragStart(
                                    canvasState.screenToWorld(downPos)
                                )
                            )
                            if (!consumed) {
                                canvasState = canvasState.copy(
                                    offset = canvasState.offset + totalDrag
                                )
                            }
                        }

                        if (isDragging) {
                            change.consume()
                            val consumed = onIntentRef(
                                ShipBuilderIntent.CanvasDragMove(
                                    worldPosition = canvasState.screenToWorld(change.position),
                                    worldDelta = dragDelta / canvasState.zoom,
                                )
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
        canvasState = canvasState.copy(
            viewportCentre = size.center,
        )
        drawGrid(canvasState)
        val creating = state.editorMode as? EditorMode.CreatingItem
        drawPlacedItems(state, canvasState, alpha = if (creating != null) 0.3f else 1f)
        if (creating != null) {
            drawCreationPolygon(
                vertices = creating.vertices,
                selectedVertexIndex = creating.selectedVertexIndex,
                isConvex = creating.isConvex,
                canvasState = canvasState,
            )
        }
    }
}

private fun DrawScope.drawPlacedItems(
    state: ShipBuilderState,
    canvasState: CanvasState,
    alpha: Float = 1f,
) {
    val selectedId = state.selectedItemId

    for (placed in state.placedHulls) {
        val hullDef = state.itemDefinitions.find { it.id == placed.itemDefinitionId } ?: continue
        val isSelected = placed.id == selectedId
        drawHullPiece(
            piece = placed,
            vertices = hullDef.vertices,
            isSelected = isSelected,
            canvasState = canvasState,
            alpha = alpha,
        )
        if (isSelected && alpha >= 1f) {
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
            alpha = alpha,
        )
        if (isSelected && alpha >= 1f) {
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
            alpha = alpha,
        )
        if (isSelected && alpha >= 1f) {
            val worldPos = Offset(placed.position.x.raw, placed.position.y.raw)
            drawRotateHandle(
                itemWorldPos = worldPos,
                itemRotation = placed.rotation.normalized,
                canvasState = canvasState,
            )
        }
    }
}
