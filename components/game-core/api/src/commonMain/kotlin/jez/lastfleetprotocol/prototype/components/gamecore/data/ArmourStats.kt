package jez.lastfleetprotocol.prototype.components.gamecore.data

/**
 * Uniform armour stats applied to all hull segments of a ship.
 * Per-segment overrides deferred to a future slice.
 */
data class ArmourStats(
    val hardness: Float,
    val density: Float,
)
