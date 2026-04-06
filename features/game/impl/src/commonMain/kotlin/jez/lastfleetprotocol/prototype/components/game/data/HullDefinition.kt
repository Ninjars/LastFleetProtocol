package jez.lastfleetprotocol.prototype.components.game.data

import com.pandulapeter.kubriko.types.SceneOffset

/**
 * Defines the ship hull polygon and its armour.
 * Vertices are relative to ship center; PolygonCollisionMask will auto-generate convex hull.
 */
data class HullDefinition(
    val vertices: List<SceneOffset>,
    val armour: ArmourStats,
    val mass: Float,
)
