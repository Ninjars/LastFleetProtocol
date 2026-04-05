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
import jez.lastfleetprotocol.prototype.components.game.utils.PidController
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

    /** PID controller for smooth rotation toward a target heading. */
    private val rotationPid = PidController(
        kp = ROTATION_PID_KP,
        ki = ROTATION_PID_KI,
        kd = ROTATION_PID_KD,
        integralLimit = ROTATION_PID_INTEGRAL_LIMIT,
    )

    /** Average distance from ship centre to hull vertices, used as a proximity radius. */
    private val hullRadius: Float = if (spec.hull.vertices.isNotEmpty()) {
        spec.hull.vertices.map { it.length().raw }.average().toFloat()
    } else {
        ARRIVAL_THRESHOLD
    }

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
     * Navigation: aggressive acceleration/deceleration with combat-aware facing
     * and optimal braking orientation.
     *
     * Thrust phase: accelerate hard toward destination at full power on all axes.
     * Brake phase: choose the most efficient braking orientation by comparing
     * time-to-stop using current facing vs rotating to present the strongest
     * available thrust axis against velocity. Accounts for disabled engines.
     *
     * Between manoeuvres, rotates to face the combat target (armoured prow
     * toward threats). When no combat target exists, faces movement direction.
     */
    private fun navigateToDestination(deltaMs: Int) {
        val dest = destination
        if (dest == null) {
            // No destination: brake to a stop using the strongest available thrust
            physics.decelerate(spec.movementConfig.forwardThrust, deltaMs)
            physics.decelerateAngular(ANGULAR_DRAG, deltaMs)
            rotateToCombatTarget(deltaMs)
            return
        }

        val toTarget = dest - body.position
        val distanceToTarget = toTarget.length()
        val speed = physics.speed()

        // Snap-to-stop: when close to destination (proportional to ship size)
        // and moving slowly, decelerate to a stop rather than risking overshoot.
        val proximityRadius = hullRadius * PROXIMITY_RADIUS_FACTOR
        val slowThreshold = hullRadius * SLOW_SPEED_FACTOR
        if (distanceToTarget.raw < proximityRadius && speed.raw < slowThreshold) {
            destination = null
            physics.decelerate(spec.movementConfig.forwardThrust, deltaMs)
            rotateToCombatTarget(deltaMs)
            return
        }

        // Hard arrival: within a very small radius, stop regardless of speed
        if (distanceToTarget.raw < ARRIVAL_THRESHOLD) {
            destination = null
            physics.decelerate(spec.movementConfig.forwardThrust, deltaMs)
            rotateToCombatTarget(deltaMs)
            return
        }

        val facing = body.rotation
        val velocity = physics.velocity

        val maxSpeed = spec.movementConfig.forwardThrust / spec.totalMass * MAX_SPEED_FACTOR

        // Determine optimal braking strategy
        val braking = computeBrakingStrategy(speed.raw, facing)

        val isBraking = braking.stoppingDistance >= distanceToTarget.raw * BRAKING_MARGIN

        val targetDirection = toTarget.normalized()
        val desiredSpeed = if (isBraking) {
            val decel = braking.effectiveDeceleration
            if (decel > 0f) {
                kotlin.math.sqrt(2f * decel * distanceToTarget.raw).coerceAtMost(maxSpeed)
            } else {
                0f
            }
        } else {
            maxSpeed
        }
        val desiredVelocity = targetDirection * desiredSpeed

        val correction = desiredVelocity - velocity
        val correctionMagnitude = correction.length().raw

        if (correctionMagnitude < CORRECTION_EPSILON) {
            rotateToCombatTarget(deltaMs)
            return
        }

        // Decompose correction into forward/lateral relative to current facing
        val facingCos = facing.cos
        val facingSin = facing.sin
        val forwardComponent = correction.x.raw * facingCos + correction.y.raw * facingSin
        val lateralComponent = -correction.x.raw * facingSin + correction.y.raw * facingCos

        // --- Thrust ---
        if (isBraking && braking.shouldRotate) {
            // Optimal braking: rotate to present best thrust axis against velocity,
            // then apply full thrust on that axis. During rotation, use whatever
            // axes are available to start slowing.
            val brakingAngle = braking.optimalBrakingAngle
            val rawDelta = brakingAngle - facing
            val angleDelta = normalizeAngle(rawDelta)
            val absAngle = absAngleOf(angleDelta)

            // While rotating, still apply whatever braking we can from current facing
            applyCurrentFacingBrake(forwardComponent, lateralComponent, facing)

            // Rotate toward optimal braking orientation
            applyRotationToward(angleDelta, absAngle, deltaMs)
            return
        }

        if (forwardComponent > CORRECTION_EPSILON) {
            physics.applyThrust(
                SceneOffset(1f.sceneUnit, 0f.sceneUnit),
                spec.movementConfig.forwardThrust,
                facing,
            )
        } else if (forwardComponent < -CORRECTION_EPSILON) {
            physics.applyThrust(
                SceneOffset((-1f).sceneUnit, 0f.sceneUnit),
                spec.movementConfig.reverseThrust,
                facing,
            )
        }

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
        if (isBraking || correctionMagnitude < maxSpeed * 0.3f) {
            rotateToCombatTarget(deltaMs)
        } else {
            val correctionAngle = kotlin.math.atan2(
                correction.y.raw,
                correction.x.raw,
            ).rad
            val rawDelta = correctionAngle - facing
            val angleDelta = normalizeAngle(rawDelta)
            applyRotationToward(angleDelta, absAngleOf(angleDelta), deltaMs)
        }
    }

    /**
     * Apply braking thrust from the current facing orientation using whichever
     * axes oppose velocity, without rotating.
     */
    private fun applyCurrentFacingBrake(
        forwardComponent: Float,
        lateralComponent: Float,
        facing: AngleRadians,
    ) {
        // Apply reverse if velocity has a forward component (ship moving forward)
        if (forwardComponent < -CORRECTION_EPSILON) {
            physics.applyThrust(
                SceneOffset((-1f).sceneUnit, 0f.sceneUnit),
                spec.movementConfig.reverseThrust,
                facing,
            )
        } else if (forwardComponent > CORRECTION_EPSILON) {
            physics.applyThrust(
                SceneOffset(1f.sceneUnit, 0f.sceneUnit),
                spec.movementConfig.forwardThrust,
                facing,
            )
        }
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
    }

    /**
     * Compute the best braking strategy by comparing stopping with current
     * orientation vs rotating to present the strongest thrust axis against
     * current velocity.
     *
     * For each thrust axis (forward, reverse, lateral), computes the angle
     * needed to align that axis against velocity, estimates rotation time,
     * and calculates total time to stop (rotation + deceleration). Picks
     * the fastest option. Handles disabled engines (zero thrust) gracefully.
     */
    private fun computeBrakingStrategy(speed: Float, facing: AngleRadians): BrakingStrategy {
        if (speed < CORRECTION_EPSILON) {
            return BrakingStrategy(
                stoppingDistance = 0f,
                effectiveDeceleration = 0f,
                shouldRotate = false,
                optimalBrakingAngle = facing,
            )
        }

        val mass = spec.totalMass
        val mc = spec.movementConfig
        val vel = physics.velocity

        // Direction opposite to velocity (where we want to thrust)
        val antiVelAngle = kotlin.math.atan2(-vel.y.raw, -vel.x.raw)

        // Angular acceleration for rotation time estimates
        val angularAccel = if (mass > 0f) mc.angularThrust / mass else 0f

        // Candidate axes: (localAngle offset from forward, thrust magnitude)
        // Forward (+X) opposing velocity means ship faces opposite to velocity
        // Reverse (-X) opposing velocity means ship faces same direction as velocity
        // Lateral (+Y/-Y) opposing velocity means ship faces 90 degrees off
        data class Candidate(
            val thrustMagnitude: Float,
            val localAngleOffset: Float, // radians to add to antiVelAngle to get ship facing
        )

        val candidates = mutableListOf<Candidate>()

        // Forward axis opposing velocity: ship faces antiVelAngle (forward = +X points anti-vel)
        if (mc.forwardThrust > 0f) {
            candidates.add(Candidate(mc.forwardThrust, 0f))
        }
        // Reverse axis opposing velocity: ship faces velAngle (reverse = -X points anti-vel)
        if (mc.reverseThrust > 0f) {
            candidates.add(Candidate(mc.reverseThrust, kotlin.math.PI.toFloat()))
        }
        // Lateral +Y opposing velocity: ship rotated -90 from anti-vel
        if (mc.lateralThrust > 0f) {
            candidates.add(Candidate(mc.lateralThrust, -kotlin.math.PI.toFloat() / 2f))
            candidates.add(Candidate(mc.lateralThrust, kotlin.math.PI.toFloat() / 2f))
        }

        if (candidates.isEmpty()) {
            return BrakingStrategy(
                stoppingDistance = Float.MAX_VALUE,
                effectiveDeceleration = 0f,
                shouldRotate = false,
                optimalBrakingAngle = facing,
            )
        }

        // Current-facing braking: how fast can we decelerate right now?
        val facingCos = facing.cos
        val facingSin = facing.sin
        // Component of each axis along the anti-velocity direction
        val antiVelX = -vel.x.raw / speed
        val antiVelY = -vel.y.raw / speed
        // Forward axis in world: (cos(facing), sin(facing))
        val fwdDot = facingCos * antiVelX + facingSin * antiVelY
        // Reverse axis: (-cos(facing), -sin(facing))
        val revDot = -fwdDot
        // Lateral axis: (-sin(facing), cos(facing))
        val latDot = -facingSin * antiVelX + facingCos * antiVelY

        val currentDecel = (
                maxOf(0f, fwdDot) * mc.forwardThrust +
                        maxOf(0f, revDot) * mc.reverseThrust +
                        kotlin.math.abs(latDot) * mc.lateralThrust
                ) / mass

        val currentStopTime = if (currentDecel > 0f) speed / currentDecel else Float.MAX_VALUE
        val currentStopDist = if (currentDecel > 0f) {
            (speed * speed) / (2f * currentDecel)
        } else {
            Float.MAX_VALUE
        }

        // Evaluate each rotation candidate
        var bestTime = currentStopTime
        var bestAngle = facing.normalized
        var bestDecel = currentDecel
        var bestShouldRotate = false

        for (candidate in candidates) {
            val decel = candidate.thrustMagnitude / mass
            if (decel <= 0f) continue

            val targetFacing = antiVelAngle + candidate.localAngleOffset
            val rawDelta = targetFacing - facing.normalized
            val angleToRotate = kotlin.math.abs(
                if (rawDelta > kotlin.math.PI.toFloat()) rawDelta - 2f * kotlin.math.PI.toFloat()
                else if (rawDelta < -kotlin.math.PI.toFloat()) rawDelta + 2f * kotlin.math.PI.toFloat()
                else rawDelta
            )

            // Estimate rotation time: theta = 0.5 * alpha * t^2 for half,
            // then decelerate for the other half. Total: t = 2 * sqrt(theta / alpha)
            val rotationTime = if (angularAccel > 0f) {
                2f * kotlin.math.sqrt(angleToRotate / angularAccel)
            } else {
                Float.MAX_VALUE
            }

            // Distance traveled during rotation (at current speed, roughly)
            val distDuringRotation = speed * rotationTime

            // Braking time after rotation
            val brakingTime = speed / decel

            val totalTime = rotationTime + brakingTime

            if (totalTime < bestTime * ROTATION_BRAKE_THRESHOLD) {
                bestTime = totalTime
                bestAngle = targetFacing
                bestDecel = decel
                bestShouldRotate = true
            }
        }

        val stoppingDistance = if (bestShouldRotate) {
            // Distance during rotation + braking distance after aligned
            val rotRawDelta = bestAngle - facing.normalized
            val rotAngle = kotlin.math.abs(
                if (rotRawDelta > kotlin.math.PI.toFloat()) rotRawDelta - 2f * kotlin.math.PI.toFloat()
                else if (rotRawDelta < -kotlin.math.PI.toFloat()) rotRawDelta + 2f * kotlin.math.PI.toFloat()
                else rotRawDelta
            )
            val rotTime = if (angularAccel > 0f) 2f * kotlin.math.sqrt(rotAngle / angularAccel) else 0f
            speed * rotTime + (speed * speed) / (2f * bestDecel)
        } else {
            currentStopDist
        }

        return BrakingStrategy(
            stoppingDistance = stoppingDistance,
            effectiveDeceleration = bestDecel,
            shouldRotate = bestShouldRotate,
            optimalBrakingAngle = bestAngle.rad,
        )
    }

    private data class BrakingStrategy(
        val stoppingDistance: Float,
        val effectiveDeceleration: Float,
        val shouldRotate: Boolean,
        val optimalBrakingAngle: AngleRadians,
    )

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

    /** Compute absolute effective angle from an AngleRadians delta. */
    private fun absAngleOf(angleDelta: AngleRadians): Float {
        val n = angleDelta.normalized
        return if (n > kotlin.math.PI.toFloat()) (2.0 * kotlin.math.PI).toFloat() - n else n
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
     * Apply angular force to rotate toward the desired heading using a PID controller.
     *
     * The PID controller takes the signed angle error and outputs a normalised torque
     * signal in [-1, 1]. This provides proportional response (large errors get full
     * thrust), derivative damping (reduces overshoot by opposing rapid angular changes),
     * and a small integral term to correct persistent steady-state error.
     */
    private fun applyRotationToward(
        angleDelta: AngleRadians,
        absAngle: Float,
        deltaMs: Int,
    ) {
        if (absAngle < ANGULAR_ARRIVAL_THRESHOLD) {
            physics.decelerateAngular(spec.movementConfig.angularThrust, deltaMs)
            rotationPid.reset()
            return
        }

        val dt = deltaMs * 0.001f
        // angleDelta is already in (-PI, PI] from normalizeAngle().
        // Extract the raw signed float value for the PID.
        // AngleRadians.normalized maps to [0, 2PI) which loses the sign,
        // so we convert back: values > PI represent negative angles.
        val normalized = angleDelta.normalized
        val signedError = if (normalized > kotlin.math.PI.toFloat()) {
            normalized - (2.0 * kotlin.math.PI).toFloat()
        } else {
            normalized
        }

        val pidOutput = rotationPid.update(signedError, dt)

        // Clamp to [-1, 1] and scale by max angular thrust
        val torqueScale = pidOutput.coerceIn(-1f, 1f)
        physics.applyAngularForce(torqueScale * spec.movementConfig.angularThrust)
    }

    companion object {
        const val TEAM_PLAYER = "player"
        const val TEAM_ENEMY = "enemy"

        private const val ANGULAR_DRAG = 200f
        private const val ARRIVAL_THRESHOLD = 5f
        private const val ANGULAR_ARRIVAL_THRESHOLD = 0.01f

        /** Correction vectors smaller than this are treated as zero */
        private const val CORRECTION_EPSILON = 0.1f

        /** Factor to derive max approach speed from forward acceleration */
        private const val MAX_SPEED_FACTOR = 10f

        /** Start braking when stopping distance >= this fraction of remaining distance */
        private const val BRAKING_MARGIN = 0.8f

        /** Rotation braking must be this much faster than current-facing braking to trigger */
        private const val ROTATION_BRAKE_THRESHOLD = 0.75f

        /** Snap-to-stop proximity as a multiple of hullRadius */
        private const val PROXIMITY_RADIUS_FACTOR = 1.5f

        /** Speed threshold for snap-to-stop as a multiple of hullRadius (scene units/sec) */
        private const val SLOW_SPEED_FACTOR = 1.0f

        // PID controller gains for rotation.
        // kp: proportional response to angle error (full thrust at ~1 radian error)
        // ki: small integral to correct persistent drift
        // kd: derivative damping to oppose angular velocity and reduce overshoot
        private const val ROTATION_PID_KP = 3.0f
        private const val ROTATION_PID_KI = 0.1f
        private const val ROTATION_PID_KD = 2.0f
        private const val ROTATION_PID_INTEGRAL_LIMIT = 2.0f
    }
}
