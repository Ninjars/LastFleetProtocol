package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.viewModelScope
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.HullPieceDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedModule
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedTurret
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SerializableArmourStats
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ShipDesign
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ShipDesignRepository
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.CanvasInputHandler
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.snapToGrid
import jez.lastfleetprotocol.prototype.components.shipbuilder.geometry.pointInPolygon
import jez.lastfleetprotocol.prototype.components.shipbuilder.stats.calculateStats
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderIntent
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderSideEffect
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderState
import jez.lastfleetprotocol.prototype.ui.common.ViewModelContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val HIT_RADIUS_MODULE = 8f
private const val HIT_RADIUS_TURRET = 6f
private const val ROTATE_HANDLE_DISTANCE = 30f
private const val ROTATE_HANDLE_HIT_RADIUS = 12f
private const val GRID_CELL_SIZE = 10f

/**
 * Drag mode resolved at drag-start based on what was under the pointer.
 */
private enum class DragMode { NONE, MOVE_ITEM, ROTATE_ITEM, PAN }

@Inject
class ShipBuilderVM(
    private val repository: ShipDesignRepository,
) : ViewModelContract<ShipBuilderIntent, ShipBuilderState, ShipBuilderSideEffect>() {

    private val _state = MutableStateFlow(ShipBuilderState())
    override val state: StateFlow<ShipBuilderState> = _state

    private var nextId = 0
    private fun generateId(prefix: String): String = "${prefix}_${nextId++}"

    /** Current drag mode, set on drag-start, cleared on drag-end. */
    private var dragMode: DragMode = DragMode.NONE

    /** The CanvasInputHandler that the UI captures once and passes to DesignCanvas. */
    val canvasInputHandler = object : CanvasInputHandler {
        override fun onTap(worldPosition: Offset) {
            accept(ShipBuilderIntent.CanvasTap(worldPosition))
        }

        override fun onDragStart(worldPosition: Offset): Boolean {
            accept(ShipBuilderIntent.CanvasDragStart(worldPosition))
            return dragMode != DragMode.PAN && dragMode != DragMode.NONE
        }

        override fun onDragMove(worldPosition: Offset, worldDelta: Offset): Boolean {
            if (dragMode == DragMode.PAN || dragMode == DragMode.NONE) return false
            accept(ShipBuilderIntent.CanvasDragMove(worldPosition, worldDelta))
            return true
        }

        override fun onDragEnd(worldPosition: Offset) {
            accept(ShipBuilderIntent.CanvasDragEnd(worldPosition))
        }
    }

    init {
        val initialName = "Untitled Ship ${generateTimestamp()}"
        _state.update { it.copy(designName = initialName) }
        autoSave()
    }

    private fun generateTimestamp(): String {
        return "${nextId++}"
    }

    override fun accept(intent: ShipBuilderIntent) {
        when (intent) {
            is ShipBuilderIntent.Noop -> Unit

            is ShipBuilderIntent.AddHullPiece -> {
                _state.update { current ->
                    val piece = intent.catalogPiece
                    val defId = generateId("hulldef")
                    val placedId = generateId("hull")
                    val hullDef = HullPieceDefinition(
                        id = defId,
                        vertices = piece.vertices,
                        armour = SerializableArmourStats(
                            hardness = piece.armour.hardness,
                            density = piece.armour.density,
                        ),
                        sizeCategory = piece.sizeCategory,
                        mass = piece.mass,
                    )
                    val placed = PlacedHullPiece(
                        id = placedId,
                        hullPieceId = defId,
                        position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
                        rotation = 0f.rad,
                    )
                    val newState = current.copy(
                        hullPieces = current.hullPieces + hullDef,
                        placedHulls = current.placedHulls + placed,
                    )
                    recalculate(newState)
                }
                autoSave()
            }

            is ShipBuilderIntent.AddModule -> {
                _state.update { current ->
                    val moduleId = generateId("module")
                    val parentHullId = current.placedHulls.firstOrNull()?.id ?: ""
                    val placed = PlacedModule(
                        id = moduleId,
                        systemType = intent.catalogModule.type.name,
                        position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
                        rotation = 0f.rad,
                        parentHullId = parentHullId,
                    )
                    val newState = current.copy(
                        placedModules = current.placedModules + placed,
                    )
                    recalculate(newState)
                }
                autoSave()
            }

            is ShipBuilderIntent.AddTurret -> {
                _state.update { current ->
                    val turretId = generateId("turret")
                    val parentHullId = current.placedHulls.firstOrNull()?.id ?: ""
                    val placed = PlacedTurret(
                        id = turretId,
                        turretConfigId = intent.catalogTurret.configId,
                        position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
                        rotation = 0f.rad,
                        parentHullId = parentHullId,
                    )
                    val newState = current.copy(
                        placedTurrets = current.placedTurrets + placed,
                    )
                    recalculate(newState)
                }
                autoSave()
            }

            // --- Canvas input: VM resolves what the pointer events mean ---

            is ShipBuilderIntent.CanvasTap -> {
                val hitId = hitTestAllItems(intent.worldPosition, _state.value)
                _state.update { it.copy(selectedItemId = hitId) }
            }

            is ShipBuilderIntent.CanvasDragStart -> {
                val current = _state.value
                val selectedId = current.selectedItemId
                dragMode = if (selectedId != null) {
                    val itemPos = getItemWorldPos(selectedId, current)
                    if (itemPos != null && isOnRotateHandle(
                            intent.worldPosition,
                            itemPos,
                            current
                        )
                    ) {
                        DragMode.ROTATE_ITEM
                    } else if (hitTestItem(intent.worldPosition, selectedId, current)) {
                        DragMode.MOVE_ITEM
                    } else {
                        DragMode.PAN
                    }
                } else {
                    DragMode.PAN
                }
            }

            is ShipBuilderIntent.CanvasDragMove -> {
                val current = _state.value
                val selectedId = current.selectedItemId ?: return
                when (dragMode) {
                    DragMode.MOVE_ITEM -> {
                        val currentPos = getItemWorldPos(selectedId, current) ?: return
                        val newPos = currentPos + intent.worldDelta
                        _state.update { moveItem(it, selectedId, newPos) }
                    }

                    DragMode.ROTATE_ITEM -> {
                        val itemPos = getItemWorldPos(selectedId, current) ?: return
                        val angle = atan2(
                            intent.worldPosition.y - itemPos.y,
                            intent.worldPosition.x - itemPos.x,
                        )
                        _state.update { rotateItem(it, selectedId, angle) }
                    }

                    DragMode.PAN, DragMode.NONE -> {
                        // Pan is handled locally in DesignCanvas — nothing to do here
                    }
                }
            }

            is ShipBuilderIntent.CanvasDragEnd -> {
                if (dragMode == DragMode.MOVE_ITEM) {
                    val current = _state.value
                    val selectedId = current.selectedItemId
                    if (selectedId != null) {
                        val pos = getItemWorldPos(selectedId, current)
                        if (pos != null) {
                            val snapped = snapToGrid(pos, GRID_CELL_SIZE)
                            _state.update { recalculate(moveItem(it, selectedId, snapped)) }
                            autoSave()
                        }
                    }
                } else if (dragMode == DragMode.ROTATE_ITEM) {
                    autoSave()
                }
                dragMode = DragMode.NONE
            }

            is ShipBuilderIntent.RotateCW -> {
                _state.update { current ->
                    recalculate(rotateItemBy(current, intent.id, (PI / 2).toFloat()))
                }
                autoSave()
            }

            is ShipBuilderIntent.RotateCCW -> {
                _state.update { current ->
                    recalculate(rotateItemBy(current, intent.id, -(PI / 2).toFloat()))
                }
                autoSave()
            }

            is ShipBuilderIntent.MirrorItemX -> {
                _state.update { current ->
                    recalculate(mirrorItem(current, intent.id, mirrorX = true))
                }
                autoSave()
            }

            is ShipBuilderIntent.MirrorItemY -> {
                _state.update { current ->
                    recalculate(mirrorItem(current, intent.id, mirrorX = false))
                }
                autoSave()
            }

            is ShipBuilderIntent.RenameDesign -> {
                _state.update { current ->
                    current.copy(designName = intent.name)
                }
                autoSave()
            }

            is ShipBuilderIntent.LoadDesignClicked -> {
                viewModelScope.launch {
                    val designs = try {
                        repository.listAll()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    _state.update { current ->
                        current.copy(
                            showLoadDialog = true,
                            savedDesigns = designs,
                        )
                    }
                }
            }

            is ShipBuilderIntent.ConfirmLoad -> {
                viewModelScope.launch {
                    // Save current design first
                    try {
                        repository.save(_state.value.toShipDesign())
                    } catch (_: Exception) {
                    }

                    // Load selected design
                    val loaded = try {
                        repository.load(intent.name)
                    } catch (_: Exception) {
                        null
                    }

                    if (loaded != null) {
                        _state.update { current ->
                            val newState = current.copy(
                                designName = loaded.name,
                                hullPieces = loaded.hullPieces,
                                placedHulls = loaded.placedHulls,
                                placedModules = loaded.placedModules,
                                placedTurrets = loaded.placedTurrets,
                                selectedItemId = null,
                                showLoadDialog = false,
                                savedDesigns = emptyList(),
                            )
                            recalculate(newState)
                        }
                    } else {
                        _state.update { it.copy(showLoadDialog = false) }
                    }
                }
            }

            is ShipBuilderIntent.DismissLoadDialog -> {
                _state.update { current ->
                    current.copy(showLoadDialog = false, savedDesigns = emptyList())
                }
            }
        }
    }

    /**
     * Recalculate derived state: invalid placements and stats.
     */
    private fun recalculate(state: ShipBuilderState): ShipBuilderState {
        val invalidPlacements = computeInvalidPlacements(state)
        val stats = calculateStats(state)
        return state.copy(
            invalidPlacements = invalidPlacements,
            stats = stats,
        )
    }

    /**
     * Check which modules and turrets are placed outside all hull piece bounds.
     */
    private fun computeInvalidPlacements(state: ShipBuilderState): Set<String> {
        val invalid = mutableSetOf<String>()

        for (module in state.placedModules) {
            if (!isInsideAnyHull(
                    Offset(module.position.x.raw, module.position.y.raw),
                    state,
                )
            ) {
                invalid.add(module.id)
            }
        }

        for (turret in state.placedTurrets) {
            if (!isInsideAnyHull(
                    Offset(turret.position.x.raw, turret.position.y.raw),
                    state,
                )
            ) {
                invalid.add(turret.id)
            }
        }

        return invalid
    }

    /**
     * Check if a world-space point is inside any placed hull piece.
     */
    private fun isInsideAnyHull(worldPoint: Offset, state: ShipBuilderState): Boolean {
        for (placed in state.placedHulls) {
            val hullDef = state.hullPieces.find { it.id == placed.hullPieceId } ?: continue
            if (hullDef.vertices.size < 3) continue

            val hullPos = Offset(placed.position.x.raw, placed.position.y.raw)
            val rotation = placed.rotation.normalized

            // Transform test point into hull local space
            val localX = worldPoint.x - hullPos.x
            val localY = worldPoint.y - hullPos.y
            val cosR = cos(-rotation)
            val sinR = sin(-rotation)
            val testX = localX * cosR - localY * sinR
            val testY = localX * sinR + localY * cosR

            val localVertices = hullDef.vertices.map { v ->
                Offset(v.x.raw, v.y.raw)
            }

            if (pointInPolygon(Offset(testX, testY), localVertices)) {
                return true
            }
        }
        return false
    }

    // --- Hit testing (moved from DesignCanvas to VM) ---

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
            val hullDef = state.hullPieces.find { it.id == placed.hullPieceId } ?: continue
            if (pointInHullPiece(worldPos, placed, hullDef.vertices)) return placed.id
        }
        return null
    }

    private fun hitTestItem(worldPos: Offset, itemId: String, state: ShipBuilderState): Boolean {
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

    private fun pointInHullPiece(
        worldPoint: Offset,
        placed: PlacedHullPiece,
        vertices: List<com.pandulapeter.kubriko.types.SceneOffset>,
    ): Boolean {
        if (vertices.size < 3) return false
        val rotation = placed.rotation.normalized
        val pos = Offset(placed.position.x.raw, placed.position.y.raw)
        val localX = worldPoint.x - pos.x
        val localY = worldPoint.y - pos.y
        val cosR = cos(-rotation)
        val sinR = sin(-rotation)
        val testPoint = Offset(localX * cosR - localY * sinR, localX * sinR + localY * cosR)
        val localVertices = vertices.map { Offset(it.x.raw, it.y.raw) }
        return pointInPolygon(testPoint, localVertices)
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

    private fun isOnRotateHandle(
        worldPos: Offset,
        itemPos: Offset,
        state: ShipBuilderState
    ): Boolean {
        val selectedId = state.selectedItemId ?: return false
        val rotation = getItemRotation(selectedId, state) ?: return false
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

    // --- Item mutation helpers ---

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

    private fun autoSave() {
        viewModelScope.launch {
            try {
                repository.save(_state.value.toShipDesign())
            } catch (_: Exception) {
                // Save failure should not crash the app
            }
        }
    }

    private fun rotateItemBy(
        state: ShipBuilderState,
        id: String,
        deltaRadians: Float
    ): ShipBuilderState {
        return state.copy(
            placedHulls = state.placedHulls.map {
                if (it.id == id) it.copy(rotation = (it.rotation.normalized + deltaRadians).rad) else it
            },
            placedModules = state.placedModules.map {
                if (it.id == id) it.copy(rotation = (it.rotation.normalized + deltaRadians).rad) else it
            },
            placedTurrets = state.placedTurrets.map {
                if (it.id == id) it.copy(rotation = (it.rotation.normalized + deltaRadians).rad) else it
            },
        )
    }

    /**
     * Mirror an item. For hull pieces, negate vertex coordinates in the hull definition.
     * For modules/turrets, negate the relevant position axis.
     *
     * [mirrorX] = true mirrors across the Y axis (negates Y coordinates -- "flip horizontal").
     * [mirrorX] = false mirrors across the X axis (negates X coordinates -- "flip vertical").
     */
    private fun mirrorItem(
        state: ShipBuilderState,
        id: String,
        mirrorX: Boolean
    ): ShipBuilderState {
        // Check if this is a hull piece -- if so, mirror the hull definition's vertices
        val hullPlaced = state.placedHulls.find { it.id == id }
        if (hullPlaced != null) {
            val hullDefId = hullPlaced.hullPieceId
            val newHullPieces = state.hullPieces.map { def ->
                if (def.id == hullDefId) {
                    def.copy(
                        vertices = def.vertices.map { v ->
                            if (mirrorX) {
                                SceneOffset(v.x, (-v.y.raw).sceneUnit)
                            } else {
                                SceneOffset((-v.x.raw).sceneUnit, v.y)
                            }
                        }
                    )
                } else def
            }
            return state.copy(hullPieces = newHullPieces)
        }

        // For modules/turrets, negate the relevant position component
        return state.copy(
            placedModules = state.placedModules.map {
                if (it.id == id) {
                    val p = it.position
                    if (mirrorX) {
                        it.copy(position = SceneOffset(p.x, (-p.y.raw).sceneUnit))
                    } else {
                        it.copy(position = SceneOffset((-p.x.raw).sceneUnit, p.y))
                    }
                } else it
            },
            placedTurrets = state.placedTurrets.map {
                if (it.id == id) {
                    val p = it.position
                    if (mirrorX) {
                        it.copy(position = SceneOffset(p.x, (-p.y.raw).sceneUnit))
                    } else {
                        it.copy(position = SceneOffset((-p.x.raw).sceneUnit, p.y))
                    }
                } else it
            },
        )
    }
}

/**
 * Convert the current builder state into a serializable [ShipDesign].
 */
fun ShipBuilderState.toShipDesign(): ShipDesign = ShipDesign(
    name = designName,
    hullPieces = hullPieces,
    placedHulls = placedHulls,
    placedModules = placedModules,
    placedTurrets = placedTurrets,
)
