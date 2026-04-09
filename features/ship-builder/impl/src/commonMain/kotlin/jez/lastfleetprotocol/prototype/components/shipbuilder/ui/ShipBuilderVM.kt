package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.viewModelScope
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemType
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedModule
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedTurret
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SerializableArmourStats
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ShipDesign
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ShipDesignRepository
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.GRID_CELL_SIZE
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.snapToGridCentre
import jez.lastfleetprotocol.prototype.components.shipbuilder.geometry.pointInPlacedItem
import jez.lastfleetprotocol.prototype.components.shipbuilder.stats.calculateStats
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.EditorMode
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

@Inject
class ShipBuilderVM(
    private val repository: ShipDesignRepository,
) : ViewModelContract<ShipBuilderIntent, ShipBuilderState, ShipBuilderSideEffect>() {

    private val _state = MutableStateFlow(ShipBuilderState())
    override val state: StateFlow<ShipBuilderState> = _state

    private var nextId = 0
    private val inputReducer = ShipBuilderInputReducer()

    init {
        nextId = repository.listAll().size
        val initialName = "Untitled Ship ${nextId++}"
        _state.update { it.copy(designName = initialName) }
        autoSave()
    }

    /**
     * Handle a canvas input intent. Returns true if consumed (item interaction),
     * false if the canvas should pan instead.
     */
    fun handleCanvasIntent(intent: ShipBuilderIntent): Boolean {
        val result = inputReducer.reduce(_state.value, intent)
        _state.value = result.state
        if (result.shouldSave) autoSave()
        return result.consumed
    }

    override fun accept(intent: ShipBuilderIntent) {
        // Canvas input intents route through the reducer.
        if (inputReducer.handles(intent)) {
            handleCanvasIntent(intent)
            return
        }

        when (intent) {
            is ShipBuilderIntent.Noop -> Unit

            is ShipBuilderIntent.AddItem -> {
                when (val attributes = intent.itemDefinition.attributes) {
                    is ItemAttributes.HullAttributes -> addHullItem(intent.itemDefinition)
                    is ItemAttributes.ModuleAttributes -> addModuleItem(
                        intent.itemDefinition,
                        attributes
                    )

                    is ItemAttributes.TurretAttributes -> addTurretItem(intent.itemDefinition)
                }
                autoSave()
            }

            is ShipBuilderIntent.RotateCW -> {
                _state.update { recalculate(rotateItemBy(it, intent.id, (PI / 2).toFloat())) }
                autoSave()
            }

            is ShipBuilderIntent.RotateCCW -> {
                _state.update { recalculate(rotateItemBy(it, intent.id, -(PI / 2).toFloat())) }
                autoSave()
            }

            is ShipBuilderIntent.MirrorItemX -> {
                _state.update { recalculate(mirrorItem(it, intent.id, mirrorX = true)) }
                autoSave()
            }

            is ShipBuilderIntent.MirrorItemY -> {
                _state.update { recalculate(mirrorItem(it, intent.id, mirrorX = false)) }
                autoSave()
            }

            is ShipBuilderIntent.RenameDesign -> {
                _state.update { it.copy(designName = intent.name) }
                autoSave()
            }

            is ShipBuilderIntent.LoadDesignClicked -> {
                viewModelScope.launch {
                    val designs = try {
                        repository.listAll()
                    } catch (e: Exception) {
                        println("Failed to load designs: $e")
                        emptyList()
                    }
                    _state.update { it.copy(showLoadDialog = true, savedDesigns = designs) }
                }
            }

            is ShipBuilderIntent.ConfirmLoad -> {
                viewModelScope.launch {
                    try {
                        repository.save(_state.value.toShipDesign())
                    } catch (e: Exception) {
                        println("Failed to save: $e")
                    }

                    val loaded = try {
                        repository.load(intent.name)
                    } catch (e: Exception) {
                        println("Failed to load: $e"); null
                    }

                    if (loaded != null) {
                        _state.update {
                            recalculate(
                                it.copy(
                                    designName = loaded.name,
                                    itemDefinitions = loaded.itemDefinitions,
                                    placedHulls = loaded.placedHulls,
                                    placedModules = loaded.placedModules,
                                    placedTurrets = loaded.placedTurrets,
                                    selectedItemId = null,
                                    showLoadDialog = false,
                                    savedDesigns = emptyList(),
                                )
                            )
                        }
                    } else {
                        _state.update { it.copy(showLoadDialog = false) }
                    }
                }
            }

            is ShipBuilderIntent.DismissLoadDialog -> {
                _state.update { it.copy(showLoadDialog = false, savedDesigns = emptyList()) }
            }

            is ShipBuilderIntent.EnterCreationMode -> {
                _state.update {
                    it.copy(
                        selectedItemId = null,
                        editorMode = EditorMode.CreatingItem(
                            itemType = intent.itemType,
                            vertices = emptyList(),
                            selectedVertexIndex = null,
                            attributes = defaultAttributesFor(intent.itemType),
                            isConvex = true,
                            name = defaultNameFor(intent.itemType),
                        ),
                    )
                }
            }

            is ShipBuilderIntent.ExitCreationMode -> {
                _state.update { it.copy(editorMode = EditorMode.EditingShip) }
            }

            is ShipBuilderIntent.FinishCreation -> finishCreation()

            is ShipBuilderIntent.UpdateCreationName -> {
                _state.update { current ->
                    val creating = current.editorMode as? EditorMode.CreatingItem
                        ?: return@update current
                    current.copy(editorMode = creating.copy(name = intent.name))
                }
            }

            is ShipBuilderIntent.UpdateCreationAttributes -> {
                _state.update { current ->
                    val creating = current.editorMode as? EditorMode.CreatingItem
                        ?: return@update current
                    current.copy(editorMode = creating.copy(attributes = intent.attributes))
                }
            }

            // Canvas intents handled by inputReducer — exhaustive when requires these branches.
            is ShipBuilderIntent.CanvasTap,
            is ShipBuilderIntent.CanvasDragStart,
            is ShipBuilderIntent.CanvasDragMove,
            is ShipBuilderIntent.CanvasDragEnd,
            is ShipBuilderIntent.PlaceVertex,
            is ShipBuilderIntent.SelectVertex,
            is ShipBuilderIntent.MoveVertex -> Unit
        }
    }

    // --- Creation ---

    private fun finishCreation() {
        val current = _state.value
        val creating = current.editorMode as? EditorMode.CreatingItem ?: return
        if (creating.vertices.size < 3 || !creating.isConvex) return

        val defId = generateId("itemdef")

        // Snap the pivot to the grid centre closest to the average of the vertices
        val avgX = creating.vertices.map { it.x }.average().toFloat()
        val avgY = creating.vertices.map { it.y }.average().toFloat()
        val pivot = snapToGridCentre(Offset(avgX, avgY), GRID_CELL_SIZE)
        val centroidPos = SceneOffset(pivot.x.sceneUnit, pivot.y.sceneUnit)

        // Store vertices relative to the pivot so the definition's origin is the pivot point
        val vertices = creating.vertices.map {
            SceneOffset((it.x - pivot.x).sceneUnit, (it.y - pivot.y).sceneUnit)
        }
        val newDef = ItemDefinition(
            id = defId,
            name = creating.name,
            vertices = vertices,
            attributes = creating.attributes,
        )

        _state.update { s ->
            var newState = s.copy(
                itemDefinitions = s.itemDefinitions + newDef,
                editorMode = EditorMode.EditingShip,
            )
            newState = when (creating.itemType) {
                ItemType.HULL -> newState.copy(
                    placedHulls = newState.placedHulls + PlacedHullPiece(
                        id = generateId("hull"),
                        itemDefinitionId = defId,
                        position = centroidPos,
                        rotation = 0f.rad,
                    )
                )

                ItemType.MODULE -> {
                    val attrs = creating.attributes as? ItemAttributes.ModuleAttributes
                        ?: return
                    newState.copy(
                        placedModules = newState.placedModules + PlacedModule(
                            id = generateId("module"),
                            itemDefinitionId = defId,
                            systemType = attrs.systemType,
                            position = centroidPos,
                            rotation = 0f.rad,
                            parentHullId = newState.placedHulls.firstOrNull()?.id ?: "",
                        )
                    )
                }

                ItemType.TURRET -> newState.copy(
                    placedTurrets = newState.placedTurrets + PlacedTurret(
                        id = generateId("turret"),
                        itemDefinitionId = defId,
                        turretConfigId = defId,
                        position = centroidPos,
                        rotation = 0f.rad,
                        parentHullId = newState.placedHulls.firstOrNull()?.id ?: "",
                    )
                )
            }
            recalculate(newState)
        }
        autoSave()
    }

    // --- Item addition ---

    /** Snap the origin to the nearest grid centre for initial placement. */
    private val snappedOrigin: SceneOffset
        get() = snapToGridCentre(Offset.Zero, GRID_CELL_SIZE).let {
            SceneOffset(it.x.sceneUnit, it.y.sceneUnit)
        }

    private fun addHullItem(itemDef: ItemDefinition) {
        _state.update { current ->
            recalculate(
                current.copy(
                    placedHulls = current.placedHulls + PlacedHullPiece(
                        id = generateId("hull"),
                        itemDefinitionId = itemDef.id,
                        position = snappedOrigin,
                        rotation = 0f.rad,
                    ),
                )
            )
        }
    }

    private fun addModuleItem(
        itemDef: ItemDefinition,
        attributes: ItemAttributes.ModuleAttributes
    ) {
        _state.update { current ->
            recalculate(
                current.copy(
                    placedModules = current.placedModules + PlacedModule(
                        id = generateId("module"),
                        itemDefinitionId = itemDef.id,
                        systemType = attributes.systemType,
                        position = snappedOrigin,
                        rotation = 0f.rad,
                        parentHullId = current.placedHulls.firstOrNull()?.id ?: "",
                    ),
                )
            )
        }
    }

    private fun addTurretItem(itemDef: ItemDefinition) {
        _state.update { current ->
            recalculate(
                current.copy(
                    placedTurrets = current.placedTurrets + PlacedTurret(
                        id = generateId("turret"),
                        itemDefinitionId = itemDef.id,
                        turretConfigId = itemDef.id,
                        position = snappedOrigin,
                        rotation = 0f.rad,
                        parentHullId = current.placedHulls.firstOrNull()?.id ?: "",
                    ),
                )
            )
        }
    }

    private fun generateId(prefix: String): String = "${prefix}_${nextId++}"

    // --- Derived state ---

    private fun recalculate(state: ShipBuilderState): ShipBuilderState {
        return state.copy(
            invalidPlacements = computeInvalidPlacements(state),
            stats = calculateStats(state),
        )
    }

    private fun computeInvalidPlacements(state: ShipBuilderState): Set<String> {
        val invalid = mutableSetOf<String>()
        for (m in state.placedModules) {
            if (!isInsideAnyHull(
                    Offset(m.position.x.raw, m.position.y.raw),
                    state
                )
            ) invalid.add(m.id)
        }
        for (t in state.placedTurrets) {
            if (!isInsideAnyHull(
                    Offset(t.position.x.raw, t.position.y.raw),
                    state
                )
            ) invalid.add(t.id)
        }
        return invalid
    }

    private fun isInsideAnyHull(worldPoint: Offset, state: ShipBuilderState): Boolean {
        for (placed in state.placedHulls) {
            val def = state.resolveItemDefinition(placed.itemDefinitionId) ?: continue
            if (pointInPlacedItem(worldPoint, placed, def.vertices)) return true
        }
        return false
    }

    // --- Item transforms ---

    private fun rotateItemBy(state: ShipBuilderState, id: String, delta: Float) = state.copy(
        placedHulls = state.placedHulls.map { if (it.id == id) it.copy(rotation = (it.rotation.normalized + delta).rad) else it },
        placedModules = state.placedModules.map { if (it.id == id) it.copy(rotation = (it.rotation.normalized + delta).rad) else it },
        placedTurrets = state.placedTurrets.map { if (it.id == id) it.copy(rotation = (it.rotation.normalized + delta).rad) else it },
    )

    private fun mirrorItem(
        state: ShipBuilderState,
        id: String,
        mirrorX: Boolean
    ): ShipBuilderState {
        return state.copy(
            placedHulls = state.placedHulls.map {
                if (it.id == id) {
                    if (mirrorX) it.copy(mirrorX = !it.mirrorX) else it.copy(mirrorY = !it.mirrorY)
                } else it
            },
            placedModules = state.placedModules.map {
                if (it.id == id) {
                    if (mirrorX) it.copy(mirrorX = !it.mirrorX) else it.copy(mirrorY = !it.mirrorY)
                } else it
            },
            placedTurrets = state.placedTurrets.map {
                if (it.id == id) {
                    if (mirrorX) it.copy(mirrorX = !it.mirrorX) else it.copy(mirrorY = !it.mirrorY)
                } else it
            },
        )
    }

    // --- Defaults ---

    private fun defaultAttributesFor(itemType: ItemType): ItemAttributes = when (itemType) {
        ItemType.HULL -> ItemAttributes.HullAttributes(
            armour = SerializableArmourStats(5f, 2f),
            sizeCategory = "Medium",
            mass = 50f
        )

        ItemType.MODULE -> ItemAttributes.ModuleAttributes(
            systemType = "REACTOR",
            maxHp = 100f,
            density = 8f,
            mass = 20f
        )

        ItemType.TURRET -> ItemAttributes.TurretAttributes(
            sizeCategory = "Medium",
            isFixed = false,
            defaultFacing = 0f,
            isLimitedRotation = false
        )
    }

    private fun defaultNameFor(itemType: ItemType) = when (itemType) {
        ItemType.HULL -> "Custom Hull $nextId"
        ItemType.MODULE -> "Custom Module $nextId"
        ItemType.TURRET -> "Custom Turret $nextId"
    }

    private fun autoSave() {
        viewModelScope.launch {
            try {
                repository.save(_state.value.toShipDesign())
            } catch (_: Exception) {
            }
        }
    }
}

fun ShipBuilderState.toShipDesign(): ShipDesign = ShipDesign(
    name = designName, itemDefinitions = itemDefinitions,
    placedHulls = placedHulls, placedModules = placedModules, placedTurrets = placedTurrets,
)
