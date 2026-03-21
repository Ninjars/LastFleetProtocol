package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneUnit

data class ShipSpec(
    val acceleration: SceneUnit,
    val deceleration: SceneUnit,
    val maxSpeed: SceneUnit,
    val rotationRate: AngleRadians,
)
