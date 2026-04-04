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
import jez.lastfleetprotocol.prototype.components.game.ai.AIModule
import jez.lastfleetprotocol.prototype.components.game.physics.ShipPhysics
import jez.lastfleetprotocol.prototype.components.game.systems.ShipSystems
import org.jetbrains.compose.resources.DrawableResource
import kotlin.reflect.KClass

/**
 * A ship in the game world. Can be player-controlled or AI-controlled depending
 * on the [aiModules] provided. Team membership is identified by [teamId], which
 * is propagated to projectiles to prevent friendly fire.
 *
 * @param teamId Identifier for the ship's team (e.g., "player", "enemy").
 * @param targetProvider Provides the list of ships this ship should consider as targets.
 * @param aiModules AI behaviours to run each frame. Empty = player-controlled.
 * @param drawOrder Drawing order for rendering. Lower = drawn on top.
 */
class Ship(
    internal val spec: ShipSpec,
    private val drawable: DrawableResource,
    initialPosition: SceneOffset,
    val teamId: String,
    val targetProvider: () -> List<Ship> = { emptyList() },
    private val aiModules: List<AIModule> = emptyList(),
    initialVelocity: SceneOffset = SceneOffset.Zero,
    private val turrets: List<Turret> = emptyList(),
    val shipSystems: ShipSystems = ShipSystems(emptyList()),
    private val drawOrder: Float = 0f,
) : Targetable, Dynamic, Collidable, CollisionDetector, Parent {

    var isDestroyed: Boolean = false
        private set

    var onDestroyedCallback: ((Ship) -> Unit)? = null

    private lateinit var viewportManager: ViewportManager
    private lateinit var spriteManager: SpriteManager
    private lateinit var actorManager: ActorManager
    override val actors: List<Actor> = turrets

    override var velocity: SceneOffset
        get() = physics.velocity
        set(value) {
            physics.velocity = value
        }

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
    override val drawingOrder: Float = drawOrder

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
    }

    override fun update(deltaTimeInMilliseconds: Int) {
        // Run AI modules
        for (ai in aiModules) {
            ai.update(this, deltaTimeInMilliseconds)
        }

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
        this.destination = destination
    }

    /**
     * Navigation logic: steer and thrust toward destination, or drift with drag.
     */
    private fun navigateToDestination(deltaMs: Int) {
        val dest = destination
        if (dest == null) {
            physics.decelerate(DRIFT_DRAG, deltaMs)
            physics.decelerateAngular(ANGULAR_DRAG, deltaMs)
            return
        }

        val toTarget = dest - body.position
        val distanceToTarget = toTarget.length()

        if (distanceToTarget.raw < ARRIVAL_THRESHOLD) {
            destination = null
            physics.decelerate(spec.movementConfig.reverseThrust, deltaMs)
            physics.decelerateAngular(ANGULAR_DRAG, deltaMs)
            return
        }

        val speed = physics.speed()
        val facing = body.rotation

        val deceleration = spec.movementConfig.reverseThrust / spec.totalMass
        val stoppingDistance = if (deceleration > 0f) {
            (speed.raw * speed.raw) / (2f * deceleration)
        } else {
            0f
        }

        val targetAngle = body.position.angleTowards(dest)
        val rawDelta = targetAngle - facing
        val angleDelta = if (rawDelta > AngleRadians.Pi) {
            rawDelta - AngleRadians.TwoPi
        } else if (rawDelta < -AngleRadians.Pi) {
            rawDelta + AngleRadians.TwoPi
        } else {
            rawDelta
        }
        val absAngle = angleDelta.normalized
        val absAngleEffective = if (absAngle > kotlin.math.PI.toFloat()) {
            (2.0 * kotlin.math.PI).toFloat() - absAngle
        } else {
            absAngle
        }

        if (stoppingDistance >= distanceToTarget.raw && speed.raw > SPEED_STOP_THRESHOLD) {
            physics.decelerate(spec.movementConfig.reverseThrust, deltaMs)
        } else {
            if (absAngleEffective > LARGE_ANGLE_THRESHOLD) {
                if (spec.movementConfig.lateralThrust > 0f) {
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
                // Forward = +X in atan2/Kubriko convention
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

        applyRotationToward(angleDelta, absAngleEffective, deltaMs)
    }

    private fun applyRotationToward(
        angleDelta: AngleRadians,
        absAngle: Float,
        deltaMs: Int,
    ) {
        if (absAngle < ANGULAR_ARRIVAL_THRESHOLD) {
            physics.decelerateAngular(spec.movementConfig.angularThrust, deltaMs)
            return
        }

        val sign = if (angleDelta > AngleRadians.Zero) 1f else -1f
        physics.applyAngularForce(sign * spec.movementConfig.angularThrust)

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
        const val TEAM_PLAYER = "player"
        const val TEAM_ENEMY = "enemy"

        private const val DRIFT_DRAG = 50f
        private const val ANGULAR_DRAG = 200f
        private const val ARRIVAL_THRESHOLD = 5f
        private const val SPEED_STOP_THRESHOLD = 0.1f
        private const val LARGE_ANGLE_THRESHOLD = 0.785f
        private const val ANGULAR_ARRIVAL_THRESHOLD = 0.01f
    }
}
