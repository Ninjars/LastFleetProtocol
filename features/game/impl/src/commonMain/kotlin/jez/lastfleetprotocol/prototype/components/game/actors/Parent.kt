package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Group

interface Parent : Group {
    abstract val body: BoxBody
}