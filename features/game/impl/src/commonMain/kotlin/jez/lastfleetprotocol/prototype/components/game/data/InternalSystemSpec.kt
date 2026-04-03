package jez.lastfleetprotocol.prototype.components.game.data

/**
 * Static configuration for an internal system.
 * The disable threshold is always 2/3 of maxHp.
 * No restart delay in this slice — disabled systems stay disabled.
 */
data class InternalSystemSpec(
    val type: InternalSystemType,
    val maxHp: Float,
    val density: Float,
    val mass: Float,
) {
    val disableThreshold: Float get() = maxHp * (2f / 3f)
}

enum class InternalSystemType {
    REACTOR,
    MAIN_ENGINE,
    BRIDGE,
}
