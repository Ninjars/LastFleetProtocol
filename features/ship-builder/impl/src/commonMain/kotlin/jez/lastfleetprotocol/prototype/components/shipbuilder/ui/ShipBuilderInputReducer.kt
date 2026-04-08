package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import androidx.compose.ui.geometry.Offset
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.snapToGridCentre
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.snapToGridCorner
import jez.lastfleetprotocol.prototype.components.shipbuilder.geometry.isConvex
import jez.lastfleetprotocol.prototype.components.shipbuilder.geometry.pointInPolygon
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.EditorMode
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderIntent
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val HIT_RADIUS_MODULE = 8f
private const val HIT_RADIUS_TURRET = 6f
private const val ROTATE_HANDLE_DISTANCE = 30f
private const val ROTATE_HANDLE_HIT_RADIUS = 12f
private const val GRID_CELL_SIZE = 10f
private const val VERTEX_HIT_RADIUS = 8f
private const val MAX_VERTICES = 12

/**
 * Result of processing a canvas input intent.
 * @param state The updated state (or the same state if no change).
 * @param consumed True if the intent was consumed by an item interaction
 *   (move, rotate, vertex drag). False means the canvas should pan instead.
 * @param shouldSave True if the change warrants an auto-save.
 */
data class InputResult(
    val state: ShipBuilderState,
    val consumed: Boolean = true,
    val shouldSave: Boolean = false,
)

/**
 * Handles canvas pointer input intents and hit-testing logic.
 * Separated from the VM to keep interaction state (drag mode) and
 * spatial query logic isolated from the broader VM concerns.
 *
 * Tracks drag mode internally — this is transient gesture state,
 * not part of the design model.
 */
class ShipBuilderInputReducer {

    private enum class DragMode { NONE, MOVE_ITEM, ROTATE_ITEM, MOVE_VERTEX, PAN }

    private var dragMode: DragMode = DragMode.NONE
    private var draggingVertexIndex: Int = -1

    /**
     * Process a canvas input intent against the current state.
     * Returns the new state and whether the event was consumed (vs pan).
     */
    fun reduce(state: ShipBuilderState, intent: ShipBuilderIntent): InputResult {
        return when (intent) {
            is ShipBuilderIntent.CanvasTap -> handleTap(state, intent.worldPosition)
            is ShipBuilderIntent.CanvasDragStart -> handleDragStart(state, intent.worldPosition)
            is ShipBuilderIntent.CanvasDragMove -> handleDragMove(state, intent)
            is ShipBuilderIntent.CanvasDragEnd -> handleDragEnd(state, intent.worldPosition)
            is ShipBuilderIntent.PlaceVertex -> handlePlaceVertex(state, intent.worldPosition)
            is ShipBuilderIntent.SelectVertex -> handleSelectVertex(state, intent.index)
            is ShipBuilderIntent.MoveVertex -> handleMoveVertex(
                state,
                intent.index,
                intent.worldPosition
            )

            else -> InputResult(state) // Not a canvas input intent
        }
    }

    /** Returns true if this intent type is handled by the reducer. */
    fun handles(intent: ShipBuilderIntent): Boolean = when (intent) {
        is ShipBuilderIntent.CanvasTap,
        is ShipBuilderIntent.CanvasDragStart,
        is ShipBuilderIntent.CanvasDragMove,
        is ShipBuilderIntent.CanvasDragEnd,
        is ShipBuilderIntent.PlaceVertex,
        is ShipBuilderIntent.SelectVertex,
        is ShipBuilderIntent.MoveVertex -> true

        else -> false
    }

    // --- Tap ---

    private fun handleTap(state: ShipBuilderState, worldPos: Offset): InputResult {
        val mode = state.editorMode
        if (mode is EditorMode.CreatingItem) {
            val snapped = snapToGridCorner(worldPos, GRID_CELL_SIZE)
            val nearIdx = findNearVertex(snapped, mode.vertices, VERTEX_HIT_RADIUS)
            return if (nearIdx != null) {
                handleSelectVertex(state, nearIdx)
            } else {
                handlePlaceVertex(state, snapped)
            }
        }
        // Editing mode: hit test for selection
        val hitId = hitTestAllItems(worldPos, state)
        return InputResult(state.copy(selectedItemId = hitId))
    }

    // --- Drag ---

    private fun handleDragStart(state: ShipBuilderState, worldPos: Offset): InputResult {
        val mode = state.editorMode
        if (mode is EditorMode.CreatingItem) {
            val nearIdx = findNearVertex(worldPos, mode.vertices, VERTEX_HIT_RADIUS)
            if (nearIdx != null && nearIdx == mode.selectedVertexIndex) {
                dragMode = DragMode.MOVE_VERTEX
                draggingVertexIndex = nearIdx
                return InputResult(state, consumed = true)
            }
            dragMode = DragMode.PAN
            return InputResult(state, consumed = false)
        }

        // Editing mode
        val selectedId = state.selectedItemId
        dragMode = if (selectedId != null) {
            val itemPos = getItemWorldPos(selectedId, state)
            if (itemPos != null && isOnRotateHandle(worldPos, itemPos, selectedId, state)) {
                DragMode.ROTATE_ITEM
            } else if (hitTestItem(worldPos, selectedId, state)) {
                DragMode.MOVE_ITEM
            } else {
                DragMode.PAN
            }
        } else {
            DragMode.PAN
        }
        val consumed = dragMode != DragMode.PAN && dragMode != DragMode.NONE
        return InputResult(state, consumed = consumed)
    }

    private fun handleDragMove(
        state: ShipBuilderState,
        intent: ShipBuilderIntent.CanvasDragMove
    ): InputResult {
        return when (dragMode) {
            DragMode.MOVE_VERTEX -> {
                val creating = state.editorMode as? EditorMode.CreatingItem
                    ?: return InputResult(state, consumed = false)
                if (draggingVertexIndex < 0 || draggingVertexIndex >= creating.vertices.size)
                    return InputResult(state, consumed = false)
                val newVertices = creating.vertices.toMutableList().apply {
                    set(draggingVertexIndex, intent.worldPosition)
                }
                InputResult(
                    state.copy(
                        editorMode = creating.copy(
                            vertices = newVertices,
                            isConvex = isConvex(newVertices),
                        )
                    ),
                    consumed = true,
                )
            }

            DragMode.MOVE_ITEM -> {
                val selectedId = state.selectedItemId
                    ?: return InputResult(state, consumed = false)
                val currentPos = getItemWorldPos(selectedId, state)
                    ?: return InputResult(state, consumed = false)
                val newPos = currentPos + intent.worldDelta
                InputResult(moveItem(state, selectedId, newPos), consumed = true)
            }

            DragMode.ROTATE_ITEM -> {
                val selectedId = state.selectedItemId
                    ?: return InputResult(state, consumed = false)
                val itemPos = getItemWorldPos(selectedId, state)
                    ?: return InputResult(state, consumed = false)
                val angle = atan2(
                    intent.worldPosition.y - itemPos.y,
                    intent.worldPosition.x - itemPos.x,
                )
                InputResult(rotateItem(state, selectedId, angle), consumed = true)
            }

            DragMode.PAN, DragMode.NONE -> InputResult(state, consumed = false)
        }
    }

    private fun handleDragEnd(state: ShipBuilderState, worldPos: Offset): InputResult {
        val result = when (dragMode) {
            DragMode.MOVE_VERTEX -> {
                val creating = state.editorMode as? EditorMode.CreatingItem
                if (creating != null && draggingVertexIndex in creating.vertices.indices) {
                    val snapped = snapToGridCorner(worldPos, GRID_CELL_SIZE)
                    val newVertices = creating.vertices.toMutableList().apply {
                        set(draggingVertexIndex, snapped)
                    }
                    InputResult(
                        state.copy(
                            editorMode = creating.copy(
                                vertices = newVertices,
                                isConvex = isConvex(newVertices),
                            )
                        ),
                        consumed = true,
                    )
                } else {
                    InputResult(state)
                }
            }

            DragMode.MOVE_ITEM -> {
                val selectedId = state.selectedItemId
                if (selectedId != null) {
                    val pos = getItemWorldPos(selectedId, state)
                    if (pos != null) {
                        val snapped = snapToGridCentre(pos, GRID_CELL_SIZE)
                        InputResult(moveItem(state, selectedId, snapped), shouldSave = true)
                    } else InputResult(state)
                } else InputResult(state)
            }

            DragMode.ROTATE_ITEM -> InputResult(state, shouldSave = true)

            DragMode.PAN, DragMode.NONE -> InputResult(state, consumed = false)
        }
        dragMode = DragMode.NONE
        draggingVertexIndex = -1
        return result
    }

    // --- Vertex manipulation ---

    private fun handlePlaceVertex(state: ShipBuilderState, snappedPos: Offset): InputResult {
        val creating = state.editorMode as? EditorMode.CreatingItem
            ?: return InputResult(state)
        if (creating.vertices.size >= MAX_VERTICES) return InputResult(state)
        val insertIndex = if (creating.selectedVertexIndex != null) {
            creating.selectedVertexIndex + 1
        } else {
            creating.vertices.size
        }
        val newVertices = creating.vertices.toMutableList().apply {
            add(insertIndex, snappedPos)
        }
        return InputResult(
            state.copy(
                editorMode = creating.copy(
                    vertices = newVertices,
                    selectedVertexIndex = insertIndex,
                    isConvex = isConvex(newVertices),
                )
            )
        )
    }

    private fun handleSelectVertex(state: ShipBuilderState, index: Int): InputResult {
        val creating = state.editorMode as? EditorMode.CreatingItem
            ?: return InputResult(state)
        if (index < 0 || index >= creating.vertices.size) return InputResult(state)
        return InputResult(
            state.copy(editorMode = creating.copy(selectedVertexIndex = index))
        )
    }

    private fun handleMoveVertex(
        state: ShipBuilderState,
        index: Int,
        worldPos: Offset
    ): InputResult {
        val creating = state.editorMode as? EditorMode.CreatingItem
            ?: return InputResult(state)
        if (index < 0 || index >= creating.vertices.size) return InputResult(state)
        val snapped = snapToGridCentre(worldPos, GRID_CELL_SIZE)
        val newVertices = creating.vertices.toMutableList().apply {
            set(index, snapped)
        }
        return InputResult(
            state.copy(
                editorMode = creating.copy(
                    vertices = newVertices,
                    isConvex = isConvex(newVertices),
                )
            )
        )
    }

    // --- Hit testing ---

    private fun hitTestAllItems(worldPos: Offset, state: ShipBuilderState): String? {
        for (placed in state.placedTurrets.asReversed()) {
            val itemPos = Offset(placed.position.x.raw, placed.position.y.raw)
            if ((worldPos - itemPos).getDistance() < HIT_RADIUS_TURRET) return placed.id
        }
        for (placed in state.placedModules.asReversed()) {
            val itemPos = Offset(placed.position.x.raw, placed.position.y.raw)
            if ((worldPos - itemPos).getDistance() < HIT_RADIUS_MODULE) return placed.id
        }
        for (placed in state.placedHulls.asReversed()) {
            val hullDef =
                state.resolveItemDefinition(placed.itemDefinitionId) ?: continue
            if (pointInHullPiece(worldPos, placed, hullDef.vertices)) return placed.id
        }
        return null
    }

    private fun hitTestItem(worldPos: Offset, itemId: String, state: ShipBuilderState): Boolean {
        for (placed in state.placedHulls) {
            if (placed.id != itemId) continue
            val hullDef =
                state.resolveItemDefinition(placed.itemDefinitionId) ?: continue
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

    private fun pointInHullPiece(
        worldPoint: Offset,
        placed: PlacedHullPiece,
        vertices: List<SceneOffset>,
    ): Boolean {
        if (vertices.size < 3) return false
        val rotation = placed.rotation.normalized
        val pos = Offset(placed.position.x.raw, placed.position.y.raw)
        val localX = worldPoint.x - pos.x
        val localY = worldPoint.y - pos.y
        val cosR = cos(-rotation)
        val sinR = sin(-rotation)
        var testX = localX * cosR - localY * sinR
        var testY = localX * sinR + localY * cosR
        // Apply inverse mirror (mirror is self-inverse) to match how vertices are rendered
        if (placed.mirrorY) testX = -testX
        if (placed.mirrorX) testY = -testY
        val localVertices = vertices.map { Offset(it.x.raw, it.y.raw) }
        return pointInPolygon(Offset(testX, testY), localVertices)
    }

    // --- Spatial queries ---

    private fun getItemWorldPos(itemId: String, state: ShipBuilderState): Offset? {
        state.placedHulls.find { it.id == itemId }?.let {
            return Offset(it.position.x.raw, it.position.y.raw)
        }
        state.placedModules.find { it.id == itemId }?.let {
            return Offset(it.position.x.raw, it.position.y.raw)
        }
        state.placedTurrets.find { it.id == itemId }?.let {
            return Offset(it.position.x.raw, it.position.y.raw)
        }
        return null
    }

    private fun isOnRotateHandle(
        worldPos: Offset,
        itemPos: Offset,
        itemId: String,
        state: ShipBuilderState,
    ): Boolean {
        val rotation = getItemRotation(itemId, state) ?: return false
        val handleX = itemPos.x + cos(rotation) * ROTATE_HANDLE_DISTANCE
        val handleY = itemPos.y + sin(rotation) * ROTATE_HANDLE_DISTANCE
        return (worldPos - Offset(handleX, handleY)).getDistance() < ROTATE_HANDLE_HIT_RADIUS
    }

    private fun getItemRotation(itemId: String, state: ShipBuilderState): Float? {
        state.placedHulls.find { it.id == itemId }?.let { return it.rotation.normalized }
        state.placedModules.find { it.id == itemId }?.let { return it.rotation.normalized }
        state.placedTurrets.find { it.id == itemId }?.let { return it.rotation.normalized }
        return null
    }

    private fun findNearVertex(
        position: Offset,
        vertices: List<Offset>,
        hitRadius: Float,
    ): Int? {
        var bestIndex: Int? = null
        var bestDist = hitRadius
        for (i in vertices.indices) {
            val dist = (position - vertices[i]).getDistance()
            if (dist < bestDist) {
                bestDist = dist
                bestIndex = i
            }
        }
        return bestIndex
    }

    // --- Item mutation ---

    private fun moveItem(state: ShipBuilderState, id: String, worldPos: Offset): ShipBuilderState {
        val newPos = SceneOffset(worldPos.x.sceneUnit, worldPos.y.sceneUnit)
        return state.copy(
            placedHulls = state.placedHulls.map { if (it.id == id) it.copy(position = newPos) else it },
            placedModules = state.placedModules.map { if (it.id == id) it.copy(position = newPos) else it },
            placedTurrets = state.placedTurrets.map { if (it.id == id) it.copy(position = newPos) else it },
        )
    }

    private fun rotateItem(state: ShipBuilderState, id: String, angle: Float): ShipBuilderState {
        val newRotation = angle.rad
        return state.copy(
            placedHulls = state.placedHulls.map { if (it.id == id) it.copy(rotation = newRotation) else it },
            placedModules = state.placedModules.map { if (it.id == id) it.copy(rotation = newRotation) else it },
            placedTurrets = state.placedTurrets.map { if (it.id == id) it.copy(rotation = newRotation) else it },
        )
    }
}
