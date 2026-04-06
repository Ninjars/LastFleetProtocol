package jez.lastfleetprotocol.prototype.components.game.ai

import jez.lastfleetprotocol.prototype.components.game.actors.Ship

/**
 * Interface for AI behaviours that can be attached to a Ship.
 * Ships can have zero or more AI modules; a ship with no AI modules
 * is player-controlled.
 */
interface AIModule {
    fun update(ship: Ship, deltaMs: Int)
}
