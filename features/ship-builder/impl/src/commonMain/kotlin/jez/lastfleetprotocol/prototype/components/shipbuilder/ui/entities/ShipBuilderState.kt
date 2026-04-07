package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities

import androidx.compose.ui.geometry.Offset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemType
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedModule
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedTurret
import jez.lastfleetprotocol.prototype.components.shipbuilder.data.PartsCatalog
import jez.lastfleetprotocol.prototype.components.shipbuilder.stats.ShipStats

sealed interface EditorMode {
    data object EditingShip : EditorMode
    data class CreatingItem(
        val itemType: ItemType,
        val vertices: List<Offset>,
        val selectedVertexIndex: Int?,
        val attributes: ItemAttributes,
        val isConvex: Boolean,
        val name: String,
    ) : EditorMode
}

data class ShipBuilderState(
    val designName: String = "New Ship",
    val itemDefinitions: List<ItemDefinition> = emptyList(),
    val placedHulls: List<PlacedHullPiece> = emptyList(),
    val placedModules: List<PlacedModule> = emptyList(),
    val placedTurrets: List<PlacedTurret> = emptyList(),
    val selectedItemId: String? = null,
    val invalidPlacements: Set<String> = emptySet(),
    val stats: ShipStats = ShipStats(),
    val showLoadDialog: Boolean = false,
    val savedDesigns: List<String> = emptyList(),
    val editorMode: EditorMode = EditorMode.EditingShip,
) {
    /** Custom item definitions (those not in the pre-defined catalog). */
    val customItemDefinitions: List<ItemDefinition>
        get() {
            val catalogIds = PartsCatalog.allItems.map { it.id }.toSet()
            return itemDefinitions.filter { it.id !in catalogIds }
        }
}
