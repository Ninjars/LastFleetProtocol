package jez.lastfleetprotocol.prototype.components.game.actors

import jez.lastfleetprotocol.prototype.components.gamecore.data.CombatStats
import jez.lastfleetprotocol.prototype.components.gamecore.data.HullDefinition
import jez.lastfleetprotocol.prototype.components.gamecore.data.MovementConfig
import jez.lastfleetprotocol.prototype.components.gamecore.data.ShipConfig

/**
 * Runtime-computed ship specification derived from ShipConfig.
 * Contains the pre-computed values needed during gameplay.
 */
data class ShipSpec(
    val totalMass: Float,
    val movementConfig: MovementConfig,
    val combatStats: CombatStats,
    val hulls: List<HullDefinition>,
) {
    companion object {
        fun fromConfig(config: ShipConfig): ShipSpec = ShipSpec(
            totalMass = config.totalMass,
            movementConfig = config.movementConfig,
            combatStats = config.combatStats,
            hulls = config.hulls,
        )
    }
}
