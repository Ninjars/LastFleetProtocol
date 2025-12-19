package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.actor.traits.Visible
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.CollisionDetector
import com.pandulapeter.kubriko.collision.mask.BoxCollisionMask
import com.pandulapeter.kubriko.collision.mask.CollisionMask
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import kotlin.reflect.KClass

/**
 * Base class for ships. Used for Player and Enemy ships alike, but with
 * different parent classes in order to allow for simpler management of
 * collisions via corresponding collidableTypes.
 */
abstract class Ship(
    private val factionColor: Color,
    initialPosition: SceneOffset,
) : Visible, Dynamic, Collidable, CollisionDetector {

    private lateinit var viewportManager: ViewportManager

    override val body: BoxBody = BoxBody(
        initialSize = SceneSize(
            width = 150.sceneUnit,
            height = 275.sceneUnit,
        ),
        initialPosition = initialPosition,
    )

    override val collisionMask: CollisionMask = BoxCollisionMask(
        initialSize = SceneSize(
            width = 150.sceneUnit,
            height = 275.sceneUnit,
        ),
        initialPosition = body.position,
    )

    override fun DrawScope.draw() {
        drawOval(
            color = factionColor,
            size = body.size.raw,
        )
    }

    override fun onAdded(kubriko: Kubriko) {
        viewportManager = kubriko.get()
    }

    override val collidableTypes: List<KClass<out Collidable>>
        get() = emptyList()

    override fun onCollisionDetected(collidables: List<Collidable>) {
//        TODO("Not yet implemented")
    }

    override fun update(deltaTimeInMilliseconds: Int) {
//        TODO("Not yet implemented")
    }
}
