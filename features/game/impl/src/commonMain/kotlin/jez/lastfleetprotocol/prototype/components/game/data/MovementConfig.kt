package jez.lastfleetprotocol.prototype.components.game.data

/**
 * Directional thrust configuration for ship movement.
 * Forward thrust is dominant; lateral/reverse are significantly weaker.
 */
data class MovementConfig(
    val forwardThrust: Float,
    val lateralThrust: Float,
    val reverseThrust: Float,
    val angularThrust: Float,
)
