package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.actor.traits.Visible
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.utils.getRelativePoint

/**
 * Used to allow actors to position themselves relative to a parent BoxBody.
 *
 * Ensure the Child is added to the ActorManager after the parent so its body
 * parameter updates are updated correctly.
 *
 * Ensure extending classes call super.onAdded() and super.update().
 */
abstract class Child(
    private val parent: Parent,
    private val offsetFromParentPivot: SceneOffset,
) : Visible, Dynamic {

    override fun onAdded(kubriko: Kubriko) {
        body.position = parent.body.getRelativePoint(offsetFromParentPivot)
        body.rotation = parent.body.rotation
        body.scale = parent.body.scale
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        body.position = parent.body.getRelativePoint(offsetFromParentPivot)
        body.rotation = parent.body.rotation
        body.scale = parent.body.scale
    }
}