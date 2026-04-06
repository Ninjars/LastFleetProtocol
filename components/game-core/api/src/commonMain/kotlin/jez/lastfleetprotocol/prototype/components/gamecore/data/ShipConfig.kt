package jez.lastfleetprotocol.prototype.components.gamecore.data

import org.jetbrains.compose.resources.DrawableResource

/**
 * Complete configuration template for a ship.
 * All game-affecting values are defined here, not in actor constructors.
 */
data class ShipConfig(
    val drawable: DrawableResource,
    val hull: HullDefinition,
    val combatStats: CombatStats,
    val movementConfig: MovementConfig,
    val internalSystems: List<InternalSystemSpec>,
    val turretConfigs: List<TurretConfig>,
) {
    /**
     * Total mass derived from hull, armour (via hull density * hull mass factor),
     * and internal systems. Used for physics calculations.
     */
    val totalMass: Float
        get() = hull.mass +
                (hull.armour.density * hull.mass * 0.1f) +
                internalSystems.sumOf { it.mass.toDouble() }.toFloat()
}

/**
 * Configuration for a turret mounted on a ship.
 */
data class TurretConfig(
    val offsetX: Float,
    val offsetY: Float,
    val pivotX: Float,
    val pivotY: Float,
    val gunData: GunData,
)
