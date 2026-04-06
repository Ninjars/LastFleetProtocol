package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import androidx.lifecycle.viewModelScope
import androidx.compose.ui.geometry.Offset
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
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.CanvasState
import jez.lastfleetprotocol.prototype.components.shipbuilder.data.CatalogHullPiece
import jez.lastfleetprotocol.prototype.components.shipbuilder.data.CatalogSystemModule
import jez.lastfleetprotocol.prototype.components.shipbuilder.data.CatalogTurretModule
import jez.lastfleetprotocol.prototype.components.shipbuilder.geometry.pointInPolygon
import jez.lastfleetprotocol.prototype.components.shipbuilder.stats.ShipStats
import jez.lastfleetprotocol.prototype.components.shipbuilder.stats.calculateStats
import jez.lastfleetprotocol.prototype.ui.common.ViewModelContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

sealed interface ShipBuilderIntent {
    data object Noop : ShipBuilderIntent
    data class Pan(val offset: Offset) : ShipBuilderIntent
    data class Zoom(val factor: Float) : ShipBuilderIntent
    data class AddHullPiece(val catalogPiece: CatalogHullPiece) : ShipBuilderIntent
    data class AddModule(val catalogModule: CatalogSystemModule) : ShipBuilderIntent
    data class AddTurret(val catalogTurret: CatalogTurretModule) : ShipBuilderIntent

    // Selection
    data class SelectItem(val id: String) : ShipBuilderIntent
    data object Deselect : ShipBuilderIntent

    // Movement
    data class MoveItem(val id: String, val position: Offset) : ShipBuilderIntent

    // Transforms
    data class MirrorItemX(val id: String) : ShipBuilderIntent
    data class MirrorItemY(val id: String) : ShipBuilderIntent
    data class RotateCW(val id: String) : ShipBuilderIntent
    data class RotateCCW(val id: String) : ShipBuilderIntent
    data class RotateItem(val id: String, val angle: Float) : ShipBuilderIntent

    // Stats panel
    data class RenameDesign(val name: String) : ShipBuilderIntent
    data object LoadDesignClicked : ShipBuilderIntent
    data class ConfirmLoad(val name: String) : ShipBuilderIntent
    data object DismissLoadDialog : ShipBuilderIntent
}

data class ShipBuilderState(
    val designName: String = "New Ship",
    val canvasState: CanvasState = CanvasState(),
    val hullPieces: List<HullPieceDefinition> = emptyList(),
    val placedHulls: List<PlacedHullPiece> = emptyList(),
    val placedModules: List<PlacedModule> = emptyList(),
    val placedTurrets: List<PlacedTurret> = emptyList(),
    val selectedItemId: String? = null,
    val invalidPlacements: Set<String> = emptySet(),
    val stats: ShipStats = ShipStats(),
    val showLoadDialog: Boolean = false,
    val savedDesigns: List<String> = emptyList(),
)

sealed interface ShipBuilderSideEffect {
    data object NavigateBack : ShipBuilderSideEffect
}

@Inject
class ShipBuilderVM(
    private val repository: ShipDesignRepository,
) : ViewModelContract<ShipBuilderIntent, ShipBuilderState, ShipBuilderSideEffect>() {

    private val _state = MutableStateFlow(ShipBuilderState())
    override val state: StateFlow<ShipBuilderState> = _state

    private var nextId = 0
    private fun generateId(prefix: String): String = "${prefix}_${nextId++}"

    init {
        // Generate initial design name and auto-save
        val initialName = "Untitled Ship ${generateTimestamp()}"
        _state.update { it.copy(designName = initialName) }
        autoSave()
    }

    private fun generateTimestamp(): String {
        // Simple incrementing name since we don't have platform time easily
        return "${nextId++}"
    }

    override fun accept(intent: ShipBuilderIntent) {
        when (intent) {
            is ShipBuilderIntent.Noop -> Unit

            is ShipBuilderIntent.Pan -> _state.update { current ->
                current.copy(
                    canvasState = current.canvasState.copy(
                        offset = current.canvasState.offset + intent.offset,
                    )
                )
            }

            is ShipBuilderIntent.Zoom -> _state.update { current ->
                val newZoom = (current.canvasState.zoom * intent.factor)
                    .coerceIn(CanvasState.MIN_ZOOM, CanvasState.MAX_ZOOM)
                current.copy(
                    canvasState = current.canvasState.copy(zoom = newZoom)
                )
            }

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

            is ShipBuilderIntent.SelectItem -> _state.update { current ->
                current.copy(selectedItemId = intent.id)
            }

            is ShipBuilderIntent.Deselect -> _state.update { current ->
                current.copy(selectedItemId = null)
            }

            is ShipBuilderIntent.MoveItem -> {
                _state.update { current ->
                    val id = intent.id
                    val newPos = SceneOffset(
                        intent.position.x.sceneUnit,
                        intent.position.y.sceneUnit,
                    )
                    val newState = current.copy(
                        placedHulls = current.placedHulls.map {
                            if (it.id == id) it.copy(position = newPos) else it
                        },
                        placedModules = current.placedModules.map {
                            if (it.id == id) it.copy(position = newPos) else it
                        },
                        placedTurrets = current.placedTurrets.map {
                            if (it.id == id) it.copy(position = newPos) else it
                        },
                    )
                    recalculate(newState)
                }
                autoSave()
            }

            is ShipBuilderIntent.RotateItem -> {
                _state.update { current ->
                    val id = intent.id
                    val newRotation = intent.angle.rad
                    val newState = current.copy(
                        placedHulls = current.placedHulls.map {
                            if (it.id == id) it.copy(rotation = newRotation) else it
                        },
                        placedModules = current.placedModules.map {
                            if (it.id == id) it.copy(rotation = newRotation) else it
                        },
                        placedTurrets = current.placedTurrets.map {
                            if (it.id == id) it.copy(rotation = newRotation) else it
                        },
                    )
                    recalculate(newState)
                }
                autoSave()
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

    private fun autoSave() {
        viewModelScope.launch {
            try {
                repository.save(_state.value.toShipDesign())
            } catch (_: Exception) {
                // Save failure should not crash the app
            }
        }
    }

    private fun rotateItemBy(state: ShipBuilderState, id: String, deltaRadians: Float): ShipBuilderState {
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
    private fun mirrorItem(state: ShipBuilderState, id: String, mirrorX: Boolean): ShipBuilderState {
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
