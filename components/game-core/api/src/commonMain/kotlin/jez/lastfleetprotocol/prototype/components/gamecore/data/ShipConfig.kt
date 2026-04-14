package jez.lastfleetprotocol.prototype.components.gamecore.data

/**
 * Complete configuration template for a ship.
 * All game-affecting values are defined here, not in actor constructors.
 */
data class ShipConfig(
    val hulls: List<HullDefinition>,
    val combatStats: CombatStats,
    val movementConfig: MovementConfig,
    val internalSystems: List<InternalSystemSpec>,
    val turretConfigs: List<TurretConfig>,
) {
    /**
     * Total mass derived from all hulls, armour contributions, and internal systems.
     */
    val totalMass: Float
        get() {
            val hullMass = hulls.sumOf { hull ->
                (hull.mass + hull.armour.density * hull.mass * 0.1f).toDouble()
            }.toFloat()
            return hullMass + internalSystems.sumOf { it.mass.toDouble() }.toFloat()
        }
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
