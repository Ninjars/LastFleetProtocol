package jez.lastfleetprotocol.prototype.components.game.actors

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.mask.PolygonCollisionMask
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.gamecore.data.HullDefinition

/**
 * A per-hull-piece child actor that provides collision detection for a single hull
 * polygon of a parent [Ship]. Each hull piece in the ship's design becomes one
 * [HullCollider], added to the Kubriko actor manager alongside the parent.
 *
 * Bullets detect collision against [HullCollider] (not [Ship] directly). On hit,
 * the bullet routes damage through [parentShip] using this collider's hull-specific
 * [hullDefinition] (including armour stats).
 */
class HullCollider(
    val parentShip: Ship,
    val hullDefinition: HullDefinition,
    initialPosition: SceneOffset,
) : Collidable, Dynamic {

    override val body = BoxBody(
        initialPosition = initialPosition,
    )

    override val collisionMask = PolygonCollisionMask(
        vertices = hullDefinition.vertices,
    )

    override val isAlwaysActive: Boolean = true

    override fun update(deltaTimeInMilliseconds: Int) {
        // Follow parent ship's pose each frame
        body.position = parentShip.body.position
        body.rotation = parentShip.body.rotation
        collisionMask.position = body.position
        collisionMask.rotation = body.rotation
    }
}
