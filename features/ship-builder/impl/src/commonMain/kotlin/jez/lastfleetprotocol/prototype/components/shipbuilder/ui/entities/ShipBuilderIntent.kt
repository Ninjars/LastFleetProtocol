package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities

import androidx.compose.ui.geometry.Offset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition

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
}
