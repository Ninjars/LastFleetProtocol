package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.Actor
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.CollisionDetector
import com.pandulapeter.kubriko.collision.mask.CircleCollisionMask
import com.pandulapeter.kubriko.collision.mask.CollisionMask
import com.pandulapeter.kubriko.helpers.extensions.angleTowards
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.minDimension
import com.pandulapeter.kubriko.helpers.extensions.normalized
import com.pandulapeter.kubriko.helpers.extensions.rotateTowards
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.pandulapeter.kubriko.types.AngleRadians
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
    private val spec: ShipSpec,
    private val drawable: DrawableResource,
    initialPosition: SceneOffset,
    initialVelocity: SceneOffset = SceneOffset.Zero,
    private val turrets: List<Turret> = emptyList(),
) : Targetable, Dynamic, Collidable, CollisionDetector, Parent {

    protected lateinit var viewportManager: ViewportManager
    protected lateinit var spriteManager: SpriteManager
    override val actors: List<Actor> = turrets

    override var velocity: SceneOffset = initialVelocity

    private var destination: SceneOffset? = null

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
        updateFacing(deltaTimeInMilliseconds)
//        updateMovement(deltaTimeInMilliseconds)
        body.position += velocity
    }

    override fun isValidTarget(): Boolean {
        return true // TODO: check health
    }

    fun setTarget(mobile: Targetable?) {
        for (turret in turrets) {
            turret.target = mobile
        }
    }

    fun moveTo(destination: SceneOffset) {
        println("moveTo $destination")
        this.destination = destination
    }

    private fun updateFacing(deltaTimeInMilliseconds: Int) {
        val destination = this.destination ?: return

        val angleToTarget = body.position.angleTowards(destination)
        val angleDelta = angleToTarget - body.rotation
        val intendedRotation = if (angleDelta > AngleRadians.Pi) {
            angleToTarget - AngleRadians.TwoPi
        } else if (angleDelta < -AngleRadians.Pi) {
            AngleRadians.TwoPi + angleToTarget
        } else {
            angleToTarget
        }
        body.rotation =
            body.rotation.rotateTowards(
                intendedRotation,
                spec.rotationRate / deltaTimeInMilliseconds
            )
    }

    private fun updateMovement(deltaTimeInMilliseconds: Int) {
        val destination = this.destination ?: return
        println("### updateMovement:\ndestination ${this.destination} position: ${body.position}")

        val position = body.position
        val angleToTarget = position.angleTowards(destination)
        val vectorToTarget = destination - position
        val distanceToTarget = vectorToTarget.length()

        println("distance ${distanceToTarget.raw}\nspeed ${velocity.length().raw}")
        if (distanceToTarget.raw < 0.01f && velocity.length().raw < 0.01f) {
            println("updateMovement: reached rest at destination")
            this.destination = null
            velocity = SceneOffset.Zero
            return
        }

        val desiredVelocity = vectorToTarget.normalized() * spec.maxSpeed
        val steering = desiredVelocity - velocity

        // Calculate the distance required to stop
        val speed = velocity.length()
        val stoppingDistance = speed * speed / (spec.deceleration * 2)

        println("stoppingDistance ${stoppingDistance.raw}")
        println("steering $steering")

        val isWithinStoppingDistance = distanceToTarget <= stoppingDistance

        if (distanceToTarget > stoppingDistance) {
            // Accelerate
            println("accelerating")
            val acceleration = steering.normalized() * spec.acceleration
            velocity += acceleration * (deltaTimeInMilliseconds / 1000f)
            if (velocity.length() > spec.maxSpeed) {
                println("at max speed")
                velocity = velocity.normalized() * spec.maxSpeed
            }
        } else {
            // Decelerate
            println("decelerating")
            val deceleration = (steering.normalized() * -1f) * spec.deceleration
            velocity += deceleration * (deltaTimeInMilliseconds / 1000f)
            if (velocity.length().raw < 0) {
                velocity = SceneOffset.Zero
            }
        }
    }
}
