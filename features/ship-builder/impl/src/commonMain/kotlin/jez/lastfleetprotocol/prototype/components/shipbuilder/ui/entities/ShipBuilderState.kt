package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities

import androidx.compose.ui.geometry.Offset
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemAttributes
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.ItemType
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedItem
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
    val libraryItems: List<ItemDefinition> = emptyList(),
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
    /**
     * Custom items available in the parts panel — sourced from the on-disk item library.
     * Pre-defined catalog items are excluded.
     */
    val customItemDefinitions: List<ItemDefinition>
        get() = libraryItems

    /**
     * Resolve an item definition by ID, checking (in priority order):
     *  1. The design's inline [itemDefinitions] (snapshot for placed items)
     *  2. The on-disk [libraryItems] (custom items)
     *  3. The pre-defined [PartsCatalog]
     */
    fun resolveItemDefinition(id: String): ItemDefinition? =
        itemDefinitions.find { it.id == id }
            ?: libraryItems.find { it.id == id }
            ?: PartsCatalog.allItems.find { it.id == id }

    /**
     * All placed items in draw order: hulls first (bottom), then modules, then turrets (top).
     */
    val allPlacedItems: List<PlacedItem>
        get() = placedHulls + placedModules + placedTurrets
}
