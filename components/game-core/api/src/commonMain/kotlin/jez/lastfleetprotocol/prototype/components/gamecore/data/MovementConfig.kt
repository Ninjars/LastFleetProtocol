package jez.lastfleetprotocol.prototype.components.gamecore.data

/**
 * Directional thrust and drag configuration for ship movement.
 *
 * Thrust values fight drag — terminal velocity is `sqrt(thrust / dragCoeff)` per axis.
 * Drag coefficients are computed at ship-load time from hull geometry and per-piece modifiers.
 * Default drag of 0f means frictionless (backward-compatible with pre-atmospheric designs).
 */
data class MovementConfig(
    val forwardThrust: Float,
    val lateralThrust: Float,
    val reverseThrust: Float,
    val angularThrust: Float,
    val forwardDragCoeff: Float = 0f,
    val lateralDragCoeff: Float = 0f,
    val reverseDragCoeff: Float = 0f,
)
