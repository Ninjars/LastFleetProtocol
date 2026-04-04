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
import com.pandulapeter.kubriko.helpers.extensions.cos
import com.pandulapeter.kubriko.helpers.extensions.get
import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.normalized
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.helpers.extensions.sin
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

    /** The ship's current combat target, used for facing between manoeuvres. */
    var combatTarget: Targetable? = null
        private set

    fun setTarget(mobile: Targetable?) {
        combatTarget = mobile
        for (turret in turrets) {
            turret.target = mobile
        }
    }

    fun moveTo(destination: SceneOffset) {
        this.destination = destination
    }

    /**
     * Navigation: aggressive acceleration/deceleration with combat-aware facing.
     *
     * The ship manoeuvres in two phases:
     * 1. **Thrust phase**: accelerate hard toward destination using all available axes.
     *    Forward thrust at full power along the correction vector, lateral thrust to
     *    cancel perpendicular drift. No fractional scaling — always full force.
     * 2. **Brake phase**: when within stopping distance, decelerate aggressively.
     *
     * Between active thrust manoeuvres (braking, coasting near destination, or when
     * velocity correction is small), the ship rotates to face its combat target.
     * This presents the armoured prow toward threats and protects engines/reactor.
     * When no combat target exists, it faces the movement direction.
     */
    private fun navigateToDestination(deltaMs: Int) {
        val dest = destination
        if (dest == null) {
            // No destination: drift and face combat target
            physics.decelerate(DRIFT_DRAG, deltaMs)
            rotateToCombatTarget(deltaMs)
            return
        }

        val toTarget = dest - body.position
        val distanceToTarget = toTarget.length()

        if (distanceToTarget.raw < ARRIVAL_THRESHOLD) {
            destination = null
            physics.decelerate(spec.movementConfig.reverseThrust, deltaMs)
            rotateToCombatTarget(deltaMs)
            return
        }

        val facing = body.rotation
        val velocity = physics.velocity
        val speed = physics.speed()

        // Desired velocity: full speed toward target, scaled down in braking zone
        val maxSpeed = spec.movementConfig.forwardThrust / spec.totalMass * MAX_SPEED_FACTOR
        val deceleration = spec.movementConfig.reverseThrust / spec.totalMass
        val stoppingDistance = if (deceleration > 0f) {
            (speed.raw * speed.raw) / (2f * deceleration)
        } else {
            0f
        }

        val targetDirection = toTarget.normalized()
        val isBraking = stoppingDistance >= distanceToTarget.raw * BRAKING_MARGIN

        val desiredSpeed = if (isBraking) {
            kotlin.math.sqrt(2f * deceleration * distanceToTarget.raw).coerceAtMost(maxSpeed)
        } else {
            maxSpeed
        }
        val desiredVelocity = targetDirection * desiredSpeed

        // Correction = what we need to change about our velocity
        val correction = desiredVelocity - velocity
        val correctionMagnitude = correction.length().raw

        if (correctionMagnitude < CORRECTION_EPSILON) {
            // Velocity already matches desired — face combat target while coasting
            rotateToCombatTarget(deltaMs)
            return
        }

        // Decompose correction into forward/lateral relative to current facing
        val facingCos = facing.cos
        val facingSin = facing.sin
        val forwardComponent = correction.x.raw * facingCos + correction.y.raw * facingSin
        val lateralComponent = -correction.x.raw * facingSin + correction.y.raw * facingCos

        // --- Thrust: always at full power, no fractional scaling ---

        if (forwardComponent > CORRECTION_EPSILON) {
            // Full forward thrust
            physics.applyThrust(
                SceneOffset(1f.sceneUnit, 0f.sceneUnit),
                spec.movementConfig.forwardThrust,
                facing,
            )
        } else if (forwardComponent < -CORRECTION_EPSILON) {
            // Full reverse thrust (braking)
            physics.applyThrust(
                SceneOffset((-1f).sceneUnit, 0f.sceneUnit),
                spec.movementConfig.reverseThrust,
                facing,
            )
        }

        // Full lateral thrust to cancel perpendicular drift
        if (kotlin.math.abs(lateralComponent) > CORRECTION_EPSILON &&
            spec.movementConfig.lateralThrust > 0f
        ) {
            val lateralSign = if (lateralComponent > 0f) 1f else -1f
            physics.applyThrust(
                SceneOffset(0f.sceneUnit, lateralSign.sceneUnit),
                spec.movementConfig.lateralThrust,
                facing,
            )
        }

        // --- Rotation priority ---
        // During braking or low-correction coasting: face combat target
        // During active acceleration: face the correction vector for efficient thrust
        if (isBraking || correctionMagnitude < maxSpeed * 0.3f) {
            rotateToCombatTarget(deltaMs)
        } else {
            val correctionAngle = kotlin.math.atan2(
                correction.y.raw,
                correction.x.raw,
            ).rad
            val rawDelta = correctionAngle - facing
            val angleDelta = normalizeAngle(rawDelta)
            val absAngle = kotlin.math.abs(angleDelta.normalized.let {
                if (it > kotlin.math.PI.toFloat()) (2.0 * kotlin.math.PI).toFloat() - it
                else it
            })
            applyRotationToward(angleDelta, absAngle, deltaMs)
        }
    }

    /**
     * Rotate to face the combat target if one exists, otherwise face velocity direction.
     * This presents the armoured prow toward threats between manoeuvres.
     */
    private fun rotateToCombatTarget(deltaMs: Int) {
        val target = combatTarget
        val facing = body.rotation

        val desiredAngle = if (target != null && target.isValidTarget()) {
            // Face the combat target
            body.position.angleTowards(target.body.position)
        } else if (physics.speed().raw > CORRECTION_EPSILON) {
            // No combat target: face velocity direction
            kotlin.math.atan2(
                physics.velocity.y.raw,
                physics.velocity.x.raw,
            ).rad
        } else {
            // Stationary, no target: just damp rotation
            physics.decelerateAngular(ANGULAR_DRAG, deltaMs)
            return
        }

        val rawDelta = desiredAngle - facing
        val angleDelta = normalizeAngle(rawDelta)
        val absAngle = kotlin.math.abs(angleDelta.normalized.let {
            if (it > kotlin.math.PI.toFloat()) (2.0 * kotlin.math.PI).toFloat() - it
            else it
        })
        applyRotationToward(angleDelta, absAngle, deltaMs)
    }

    /**
     * Normalize an angle delta to the range (-PI, PI].
     */
    private fun normalizeAngle(rawDelta: AngleRadians): AngleRadians {
        return if (rawDelta > AngleRadians.Pi) {
            rawDelta - AngleRadians.TwoPi
        } else if (rawDelta < -AngleRadians.Pi) {
            rawDelta + AngleRadians.TwoPi
        } else {
            rawDelta
        }
    }

    /**
     * Apply angular force to rotate toward the desired heading.
     * Uses angular stopping distance to prevent overshoot.
     */
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
        private const val ANGULAR_ARRIVAL_THRESHOLD = 0.01f
        /** Correction vectors smaller than this are treated as zero */
        private const val CORRECTION_EPSILON = 0.1f
        /** Factor to derive max approach speed from forward acceleration */
        private const val MAX_SPEED_FACTOR = 3f
        /** Start braking when stopping distance >= this fraction of remaining distance */
        private const val BRAKING_MARGIN = 0.8f
    }
}
