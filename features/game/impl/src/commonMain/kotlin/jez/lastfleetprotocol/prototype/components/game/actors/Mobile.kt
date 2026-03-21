package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.actor.traits.Visible
import com.pandulapeter.kubriko.types.SceneOffset

interface Mobile : Visible {
    var velocity: SceneOffset
}