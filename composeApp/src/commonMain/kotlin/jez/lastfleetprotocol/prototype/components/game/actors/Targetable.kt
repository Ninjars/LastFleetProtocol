package jez.lastfleetprotocol.prototype.components.game.actors

interface Targetable : Mobile {
    fun isValidTarget(): Boolean
}