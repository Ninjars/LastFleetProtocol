package jez.lastfleetprotocol.prototype.components.game.actors

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.actor.Actor
import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.actor.traits.Dynamic
import com.pandulapeter.kubriko.collision.Collidable
import com.pandulapeter.kubriko.collision.CollisionDetector
import com.pandulapeter.kubriko.collision.mask.PolygonCollisionMask
import com.pandulapeter.kubriko.helpers.extensions.angleTowards
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneSize
import jez.lastfleetprotocol.prototype.components.game.data.ShipConfig
import jez.lastfleetprotocol.prototype.components.game.physics.ShipPhysics
import jez.lastfleetprotocol.prototype.components.game.systems.ShipSystems
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.abs
import kotlin.reflect.KClass

/**
 * Base class for ships. Used for Player and Enemy ships alike, but with
 * different parent classes in order to allow for simpler management of
 * collisions via corresponding collidableTypes.
 */
abstract class Ship(
    internal val spec: ShipSpec,
    private val drawable: DrawableResource,
    initialPosition: SceneOffset,
    initialVelocity: SceneOffset = SceneOffset.Zero,
    private val turrets: List<Turret> = emptyList(),
    val shipSystems: ShipSystems = ShipSystems(emptyList()),
) : Targetable, Dynamic, Collidable, CollisionDetector, Parent {

    var isDestroyed: Boolean = false
        private set

    var onDestroyedCallback: ((Ship) -> Unit)? = null

    protected lateinit var viewportManager: ViewportManager
    protected lateinit var spriteManager: SpriteManager
    private lateinit var actorManager: ActorManager
    override val actors: List<Actor> = turrets

    override var velocity: SceneOffset
        get() = physics.velocity
        set(value) { physics.velocity = value }

    /** Current movement destination, exposed for debug visualisation. */
    var destination: SceneOffset? = null
        private set

    internal val physics: ShipPhysics = ShipPhysics(
        mass = spec.totalMass,
        initialVelocity = initialVelocity,
    )

    private val sprite: ImageBitmap by lazy {
        spriteManager.get(drawable) ?: throw RuntimeException("unable to load asset for Ship")
    }

    override val body: BoxBody = BoxBody(
        initialPosition = initialPosition,
    )

    override val collisionMask: PolygonCollisionMask = PolygonCollisionMask(
        vertices = spec.hull.vertices,
    )

    override val isAlwaysActive: Boolean = true

    override fun DrawScope.draw() {
        drawImage(sprite)
    }

    override fun onAdded(kubriko: Kubriko) {
        viewportManager = kubriko.get()
        spriteManager = kubriko.get()
        actorManager = kubriko.get()
        body.size = SceneSize(sprite.width.sceneUnit, sprite.height.sceneUnit)
        body.pivot = body.size.center
    }

    override val collidableTypes: List<KClass<out Collidable>>
        get() = emptyList()

    override fun onCollisionDetected(collidables: List<Collidable>) {
//        TODO("Not yet implemented")
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        navigateToDestination(deltaTimeInMilliseconds)

        val result = physics.integrate(deltaTimeInMilliseconds)
        body.position += result.positionDelta
        body.rotation += result.rotationDelta.rad

        collisionMask.position = body.position
        collisionMask.rotation = body.rotation

        checkDestruction()
    }

    override fun isValidTarget(): Boolean {
        return !isDestroyed
    }

    fun checkDestruction() {
        if (shipSystems.isReactorDestroyed() && !isDestroyed) {
            isDestroyed = true
            onDestroyedCallback?.invoke(this)
            actorManager.remove(this)
        }
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

    /**
     * Navigation logic: steer and thrust toward destination, or drift with drag.
     */
    private fun navigateToDestination(deltaMs: Int) {
        val dest = destination
        if (dest == null) {
            // No destination: apply drag to slow down over time
            physics.decelerate(DRIFT_DRAG, deltaMs)
            physics.decelerateAngular(ANGULAR_DRAG, deltaMs)
            return
        }

        val toTarget = dest - body.position
        val distanceToTarget = toTarget.length()

        // If close enough to destination, clear it and decelerate
        if (distanceToTarget.raw < ARRIVAL_THRESHOLD) {
            destination = null
            physics.decelerate(spec.movementConfig.reverseThrust, deltaMs)
            physics.decelerateAngular(ANGULAR_DRAG, deltaMs)
            return
        }

        val speed = physics.speed()
        val facing = body.rotation

        // Calculate stopping distance: v^2 / (2 * deceleration)
        // deceleration = reverseThrust / mass
        val deceleration = spec.movementConfig.reverseThrust / spec.totalMass
        val stoppingDistance = if (deceleration > 0f) {
            (speed.raw * speed.raw) / (2f * deceleration)
        } else {
            0f
        }

        // Calculate angle from ship facing to target using the same pattern
        // as the original facing code: normalize the delta to (-PI, PI].
        val targetAngle = body.position.angleTowards(dest)
        val rawDelta = targetAngle - facing
        // Normalize to (-PI, PI] range
        val angleDelta = if (rawDelta > AngleRadians.Pi) {
            rawDelta - AngleRadians.TwoPi
        } else if (rawDelta < -AngleRadians.Pi) {
            rawDelta + AngleRadians.TwoPi
        } else {
            rawDelta
        }
        // .normalized gives the absolute radian value in [0, 2PI); use it for magnitude checks
        val absAngle = angleDelta.normalized
        // Handle wrap: if > PI, the actual angle is 2PI - normalized
        val absAngleEffective = if (absAngle > kotlin.math.PI.toFloat()) {
            (2.0 * kotlin.math.PI).toFloat() - absAngle
        } else {
            absAngle
        }

        if (stoppingDistance >= distanceToTarget.raw && speed.raw > SPEED_STOP_THRESHOLD) {
            // Within stopping distance: decelerate
            physics.decelerate(spec.movementConfig.reverseThrust, deltaMs)
        } else {
            // Not within stopping distance: thrust toward target
            if (absAngleEffective > LARGE_ANGLE_THRESHOLD) {
                // Large angle: prioritize rotation, use lateral thrust
                if (spec.movementConfig.lateralThrust > 0f) {
                    // Apply lateral thrust perpendicular to facing, toward target.
                    // In atan2 convention, perpendicular to +X facing is ±Y.
                    val lateralSign = if (angleDelta > AngleRadians.Zero) 1f else -1f
                    val lateralDir = SceneOffset(
                        x = 0f.sceneUnit,
                        y = lateralSign.sceneUnit,
                    )
                    physics.applyThrust(
                        lateralDir,
                        spec.movementConfig.lateralThrust,
                        facing,
                    )
                }
            } else {
                // Small angle: apply forward thrust (strongest), fine-tune heading.
                // angleTowards uses atan2(dy,dx), so forward = +X in local space.
                val forwardDir = SceneOffset(
                    x = 1f.sceneUnit,
                    y = 0f.sceneUnit,
                )
                physics.applyThrust(
                    forwardDir,
                    spec.movementConfig.forwardThrust,
                    facing,
                )
            }
        }

        // Apply angular force to rotate toward destination
        applyRotationToward(angleDelta, absAngleEffective, deltaMs)
    }

    /**
     * Apply angular force to rotate toward the desired heading.
     */
    private fun applyRotationToward(
        angleDelta: AngleRadians,
        absAngle: Float,
        deltaMs: Int,
    ) {
        if (absAngle < ANGULAR_ARRIVAL_THRESHOLD) {
            // Close enough to target heading: just damp angular velocity
            physics.decelerateAngular(spec.movementConfig.angularThrust, deltaMs)
            return
        }

        val sign = if (angleDelta > AngleRadians.Zero) 1f else -1f
        physics.applyAngularForce(sign * spec.movementConfig.angularThrust)

        // Apply angular drag when approaching target heading to prevent overshoot
        val angularDecel = spec.movementConfig.angularThrust / spec.totalMass
        val angularStoppingDistance = if (angularDecel > 0f) {
            (physics.angularVelocity * physics.angularVelocity) / (2f * angularDecel)
        } else {
            0f
        }

        if (angularStoppingDistance >= absAngle) {
            physics.decelerateAngular(spec.movementConfig.angularThrust, deltaMs)
        }
    }

    companion object {
        /** Drag applied when drifting with no destination */
        private const val DRIFT_DRAG = 50f
        /** Angular drag for damping rotation */
        private const val ANGULAR_DRAG = 200f
        /** Distance at which the ship considers itself "arrived" */
        private const val ARRIVAL_THRESHOLD = 5f
        /** Speed below which we consider the ship stopped for braking purposes */
        private const val SPEED_STOP_THRESHOLD = 0.1f
        /** Angle (radians) above which we prioritize rotation over forward thrust (~45 degrees) */
        private const val LARGE_ANGLE_THRESHOLD = 0.785f
        /** Angle (radians) below which we consider heading aligned */
        private const val ANGULAR_ARRIVAL_THRESHOLD = 0.01f
    }
}
