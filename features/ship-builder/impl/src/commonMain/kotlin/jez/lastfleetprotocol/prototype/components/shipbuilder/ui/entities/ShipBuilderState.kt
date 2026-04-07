package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities

import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.HullPieceDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedHullPiece
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedModule
import jez.lastfleetprotocol.prototype.components.gamecore.shipdesign.PlacedTurret
import jez.lastfleetprotocol.prototype.components.shipbuilder.stats.ShipStats

data class ShipBuilderState(
    val designName: String = "New Ship",
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
