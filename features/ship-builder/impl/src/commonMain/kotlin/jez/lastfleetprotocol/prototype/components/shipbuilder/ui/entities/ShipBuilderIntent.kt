package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities

import androidx.compose.ui.geometry.Offset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemType

sealed interface ShipBuilderIntent {
    data object Noop : ShipBuilderIntent
    data class AddItem(val itemDefinition: ItemDefinition) : ShipBuilderIntent

    // Canvas input — raw pointer events from DesignCanvas
    data class CanvasTap(val worldPosition: Offset) : ShipBuilderIntent
    data class CanvasDragStart(val worldPosition: Offset) : ShipBuilderIntent
    data class CanvasDragMove(val worldPosition: Offset, val worldDelta: Offset) : ShipBuilderIntent
    data class CanvasDragEnd(val worldPosition: Offset) : ShipBuilderIntent

    // Transforms (from toolbar buttons)
    data class MirrorItemX(val id: String) : ShipBuilderIntent
    data class MirrorItemY(val id: String) : ShipBuilderIntent
    data class RotateCW(val id: String) : ShipBuilderIntent
    data class RotateCCW(val id: String) : ShipBuilderIntent

    // Stats panel
    data class RenameDesign(val name: String) : ShipBuilderIntent
    data object LoadDesignClicked : ShipBuilderIntent
    data class ConfirmLoad(val name: String) : ShipBuilderIntent
    data object DismissLoadDialog : ShipBuilderIntent

    // Vertex manipulation (creation mode)
    data class PlaceVertex(val worldPosition: Offset) : ShipBuilderIntent
    data class SelectVertex(val index: Int) : ShipBuilderIntent
    data class MoveVertex(val index: Int, val worldPosition: Offset) : ShipBuilderIntent

    // Creation mode
    data class EnterCreationMode(val itemType: ItemType) : ShipBuilderIntent
    data object ExitCreationMode : ShipBuilderIntent
    data object FinishCreation : ShipBuilderIntent
    data class UpdateCreationName(val name: String) : ShipBuilderIntent
    data class UpdateCreationAttributes(val attributes: ItemAttributes) : ShipBuilderIntent
}
