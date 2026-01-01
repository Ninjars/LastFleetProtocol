package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.actor.traits.Positionable
import com.pandulapeter.kubriko.types.SceneOffset

interface Mobile : Positionable {
    var velocity: SceneOffset
}