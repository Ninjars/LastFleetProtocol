package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.Actor
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.actor.traits.Visible
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.CollisionDetector
import com.pandulapeter.kubriko.collision.mask.CircleCollisionMask
import com.pandulapeter.kubriko.collision.mask.CollisionMask
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.minDimension
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import org.jetbrains.compose.resources.DrawableResource
import kotlin.reflect.KClass

/**
 * Base class for ships. Used for Player and Enemy ships alike, but with
 * different parent classes in order to allow for simpler management of
 * collisions via corresponding collidableTypes.
 */
abstract class Ship(
    private val drawable: DrawableResource,
    initialPosition: SceneOffset,
    initialVelocity: SceneOffset = SceneOffset.Zero,
    private val turrets: List<Turret> = emptyList(),
) : Visible, Targetable, Dynamic, Collidable, CollisionDetector, Parent {

    private lateinit var viewportManager: ViewportManager
    private lateinit var spriteManager: SpriteManager
    override val actors: List<Actor> = turrets

    override var velocity: SceneOffset = initialVelocity

    private val sprite: ImageBitmap by lazy {
        spriteManager.get(drawable) ?: throw RuntimeException("unable to load asset for Ship")
    }

    override val body: BoxBody = BoxBody(
        initialPosition = initialPosition,
    )

    override val collisionMask: CollisionMask = CircleCollisionMask(
        initialPosition = body.position,
    )

    override fun DrawScope.draw() {
        drawImage(sprite)
    }

    override fun onAdded(kubriko: Kubriko) {
        viewportManager = kubriko.get()
        spriteManager = kubriko.get()
        body.size = SceneSize(sprite.width.sceneUnit, sprite.height.sceneUnit)
        body.pivot = body.size.center
        (collisionMask as CircleCollisionMask).radius = body.size.minDimension / 2f
    }

    override val collidableTypes: List<KClass<out Collidable>>
        get() = emptyList()

    override fun onCollisionDetected(collidables: List<Collidable>) {
//        TODO("Not yet implemented")
    }

    override fun update(deltaTimeInMilliseconds: Int) {
//        body.rotation += AngleRadians.TwoPi * deltaTimeInMilliseconds / 1000f / 5f
    }

    override fun isValidTarget(): Boolean {
        return true // TODO: check health
    }

    fun setTarget(mobile: Targetable?) {
        for (turret in turrets) {
            turret.target = mobile
        }
    }
}
