package jez.lastfleetprotocol.prototype.components.gamecore.data

/**
 * Stats for a kinetic projectile, used for impact resolution.
 */
data class ProjectileStats(
    val damage: Float,
    val armourPiercing: Float,
    val toHitModifier: Float,
    val speed: Float,
    val lifetimeMs: Int,
)
