package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.actor.traits.Visible
import com.pandulapeter.kubriko.helpers.extensions.cos
import com.pandulapeter.kubriko.helpers.extensions.sin
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset

/**
 * Used to allow actors to position themselves relative to a parent BoxBody.
 *
 * Ensure the Child is added to the ActorManager after the parent so its body
 * parameter updates are updated correctly.
 *
 * Ensure extending classes call super.onAdded() and super.update().
 */
abstract class Child(
    private val parent: BoxBody,
    private val offsetFromParentPivot: SceneOffset,
) : Visible, Dynamic {

    override fun onAdded(kubriko: Kubriko) {
        body.position = parent.getRelativePoint(offsetFromParentPivot)
        body.rotation = parent.rotation
        body.scale = parent.scale
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        body.position = parent.getRelativePoint(offsetFromParentPivot)
        body.rotation = parent.rotation
        body.scale = parent.scale
    }

    private fun BoxBody.getRelativePoint(point: SceneOffset): SceneOffset {
        val scaled = (point - pivot) * scale
        val rotated = if (rotation == AngleRadians.Zero) scaled else SceneOffset(
            x = (scaled.x + pivot.x) * rotation.cos - (scaled.y + pivot.y) * rotation.sin,
            y = (scaled.x + pivot.x) * rotation.sin + (scaled.y + pivot.y) * rotation.cos,
        )
        return rotated + position
    }
}