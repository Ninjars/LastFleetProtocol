package jez.lastfleetprotocol.prototype.components.shipbuilder.ui

import androidx.compose.ui.geometry.Offset
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.HullPieceDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedModule
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedTurret
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.SerializableArmourStats
import jez.lastfleetprotocol.prototype.components.shipbuilder.canvas.CanvasState
import jez.lastfleetprotocol.prototype.components.shipbuilder.data.CatalogHullPiece
import jez.lastfleetprotocol.prototype.components.shipbuilder.data.CatalogSystemModule
import jez.lastfleetprotocol.prototype.components.shipbuilder.data.CatalogTurretModule
import jez.lastfleetprotocol.prototype.ui.common.ViewModelContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import me.tatarka.inject.annotations.Inject

sealed interface ShipBuilderIntent {
    data object Noop : ShipBuilderIntent
    data class Pan(val offset: Offset) : ShipBuilderIntent
    data class Zoom(val factor: Float) : ShipBuilderIntent
    data class AddHullPiece(val catalogPiece: CatalogHullPiece) : ShipBuilderIntent
    data class AddModule(val catalogModule: CatalogSystemModule) : ShipBuilderIntent
    data class AddTurret(val catalogTurret: CatalogTurretModule) : ShipBuilderIntent
}

data class ShipBuilderState(
    val designName: String = "New Ship",
    val canvasState: CanvasState = CanvasState(),
    val hullPieces: List<HullPieceDefinition> = emptyList(),
    val placedHulls: List<PlacedHullPiece> = emptyList(),
    val placedModules: List<PlacedModule> = emptyList(),
    val placedTurrets: List<PlacedTurret> = emptyList(),
)

sealed interface ShipBuilderSideEffect {
    data object NavigateBack : ShipBuilderSideEffect
}

@Inject
class ShipBuilderVM : ViewModelContract<ShipBuilderIntent, ShipBuilderState, ShipBuilderSideEffect>() {

    private val _state = MutableStateFlow(ShipBuilderState())
    override val state: StateFlow<ShipBuilderState> = _state

    private var nextId = 0
    private fun generateId(prefix: String): String = "${prefix}_${nextId++}"

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

            is ShipBuilderIntent.AddHullPiece -> _state.update { current ->
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
                current.copy(
                    hullPieces = current.hullPieces + hullDef,
                    placedHulls = current.placedHulls + placed,
                )
            }

            is ShipBuilderIntent.AddModule -> _state.update { current ->
                val moduleId = generateId("module")
                // Assign to the first hull if available, or empty string
                val parentHullId = current.placedHulls.firstOrNull()?.id ?: ""
                val placed = PlacedModule(
                    id = moduleId,
                    systemType = intent.catalogModule.type.name,
                    position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
                    rotation = 0f.rad,
                    parentHullId = parentHullId,
                )
                current.copy(
                    placedModules = current.placedModules + placed,
                )
            }

            is ShipBuilderIntent.AddTurret -> _state.update { current ->
                val turretId = generateId("turret")
                val parentHullId = current.placedHulls.firstOrNull()?.id ?: ""
                val placed = PlacedTurret(
                    id = turretId,
                    turretConfigId = intent.catalogTurret.configId,
                    position = SceneOffset(0f.sceneUnit, 0f.sceneUnit),
                    rotation = 0f.rad,
                    parentHullId = parentHullId,
                )
                current.copy(
                    placedTurrets = current.placedTurrets + placed,
                )
            }
        }
    }
}
