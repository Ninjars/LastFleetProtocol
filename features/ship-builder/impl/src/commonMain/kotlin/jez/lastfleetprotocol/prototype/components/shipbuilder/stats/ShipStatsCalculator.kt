package jez.lastfleetprotocol.prototype.components.shipbuilder.stats

import jez.lastfleetprotocol.prototype.components.gamecore.stats.ShipStats
import jez.lastfleetprotocol.prototype.components.gamecore.stats.calculateShipStats
import jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities.ShipBuilderState

/**
 * Calculates ship stats from the current builder state.
 *
 * Delegates to the shared [calculateShipStats] core in game-core/api, passing
 * the builder state's placed items and its item-definition resolver.
 */
fun calculateStats(state: ShipBuilderState): ShipStats =
    calculateShipStats(
        placedHulls = state.placedHulls,
        placedModules = state.placedModules,
        placedTurrets = state.placedTurrets,
        resolveItem = state::resolveItemDefinition,
    )
