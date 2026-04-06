package jez.lastfleetprotocol.prototype.components.game.navigation

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.angleTowards
import com.pandulapeter.kubriko.helpers.extensions.cos
import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.normalized
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.helpers.extensions.sin
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.actors.Targetable
import jez.lastfleetprotocol.prototype.components.game.data.MovementConfig
import jez.lastfleetprotocol.prototype.components.game.physics.ShipPhysics
import jez.lastfleetprotocol.prototype.components.game.utils.PidController

/**
 * Resolves navigation forces for a ship: thrust toward destination, braking,
 * and combat-aware rotation. Extracted from Ship to make navigation logic
 * easier to modify or replace independently.
 *
 * Owns the PID controller for rotation and all navigation tuning constants.
 * Stateless with respect to the ship — all mutable game state is passed in
 * and the updated destination is returned.
 *
 * @param hullRadius Average distance from ship centre to hull vertices,
 *   used for proximity-based arrival checks.
 */
class ShipNavigator(
    private val hullRadius: Float,
    private val mass: Float,
) {
    private val rotationPid = PidController(
        kp = ROTATION_PID_KP,
        ki = ROTATION_PID_KI,
        kd = ROTATION_PID_KD,
        integralLimit = ROTATION_PID_INTEGRAL_LIMIT,
    )

    /**
     * Apply navigation forces to the physics system: thrust toward [destination],
     * decelerate when arriving, and rotate to face the [combatTarget] or movement
     * direction.
     *
     * @return The updated destination — `null` if the ship has arrived and cleared it,
     *   or the original [destination] if still in transit.
     */
    fun navigate(
        movementConfig: MovementConfig,
        physics: ShipPhysics,
        body: BoxBody,
        combatTarget: Targetable?,
        destination: SceneOffset?,
        deltaMs: Int,
    ): SceneOffset? {
        if (destination == null) {
            // No destination: brake to a stop
            physics.decelerate(movementConfig.forwardThrust, deltaMs)
            physics.decelerateAngular(ANGULAR_DRAG, deltaMs)
            rotateToCombatTarget(movementConfig, physics, body, combatTarget, deltaMs)
            return null
        }

        val toTarget = destination - body.position
        val distanceToTarget = toTarget.length()
        val speed = physics.speed()

        // Snap-to-stop: close to destination and moving slowly
        val proximityRadius = hullRadius * PROXIMITY_RADIUS_FACTOR
        val slowThreshold = hullRadius * SLOW_SPEED_FACTOR
        if (distanceToTarget.raw < proximityRadius && speed.raw < slowThreshold) {
            physics.decelerate(movementConfig.forwardThrust, deltaMs)
            rotateToCombatTarget(movementConfig, physics, body, combatTarget, deltaMs)
            return null
        }

        // Hard arrival: within a very small radius, stop regardless of speed
        if (distanceToTarget.raw < ARRIVAL_THRESHOLD) {
            physics.decelerate(movementConfig.forwardThrust, deltaMs)
            rotateToCombatTarget(movementConfig, physics, body, combatTarget, deltaMs)
            return null
        }

        val facing = body.rotation
        val velocity = physics.velocity

        val maxSpeed = movementConfig.forwardThrust / mass * MAX_SPEED_FACTOR

        val braking = computeBrakingStrategy(movementConfig, physics, speed.raw, facing)
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
            rotateToCombatTarget(movementConfig, physics, body, combatTarget, deltaMs)
            return destination
        }

        // Decompose correction into forward/lateral relative to current facing
        val facingCos = facing.cos
        val facingSin = facing.sin
        val forwardComponent = correction.x.raw * facingCos + correction.y.raw * facingSin
        val lateralComponent = -correction.x.raw * facingSin + correction.y.raw * facingCos

        // --- Thrust ---
        if (isBraking && braking.shouldRotate) {
            val brakingAngle = braking.optimalBrakingAngle
            val rawDelta = brakingAngle - facing
            val angleDelta = normalizeAngle(rawDelta)
            val absAngle = absAngleOf(angleDelta)

            applyCurrentFacingBrake(movementConfig, physics, forwardComponent, lateralComponent, facing)
            applyRotationToward(movementConfig, physics, angleDelta, absAngle, deltaMs)
            return destination
        }

        if (forwardComponent > CORRECTION_EPSILON) {
            physics.applyThrust(
                SceneOffset(1f.sceneUnit, 0f.sceneUnit),
                movementConfig.forwardThrust,
                facing,
            )
        } else if (forwardComponent < -CORRECTION_EPSILON) {
            physics.applyThrust(
                SceneOffset((-1f).sceneUnit, 0f.sceneUnit),
                movementConfig.reverseThrust,
                facing,
            )
        }

        if (kotlin.math.abs(lateralComponent) > CORRECTION_EPSILON &&
            movementConfig.lateralThrust > 0f
        ) {
            val lateralSign = if (lateralComponent > 0f) 1f else -1f
            physics.applyThrust(
                SceneOffset(0f.sceneUnit, lateralSign.sceneUnit),
                movementConfig.lateralThrust,
                facing,
            )
        }

        // --- Rotation priority ---
        val lateralDominant = kotlin.math.abs(lateralComponent) > kotlin.math.abs(forwardComponent) &&
            movementConfig.lateralThrust > 0f
        if (isBraking || correctionMagnitude < maxSpeed * 0.3f || lateralDominant) {
            rotateToCombatTarget(movementConfig, physics, body, combatTarget, deltaMs)
        } else {
            val correctionAngle = kotlin.math.atan2(
                correction.y.raw,
                correction.x.raw,
            ).rad
            val rawDelta = correctionAngle - facing
            val angleDelta = normalizeAngle(rawDelta)
            applyRotationToward(movementConfig, physics, angleDelta, absAngleOf(angleDelta), deltaMs)
        }

        return destination
    }

    // -- Rotation --

    private fun rotateToCombatTarget(
        movementConfig: MovementConfig,
        physics: ShipPhysics,
        body: BoxBody,
        combatTarget: Targetable?,
        deltaMs: Int,
    ) {
        val facing = body.rotation

        val desiredAngle = if (combatTarget != null && combatTarget.isValidTarget()) {
            body.position.angleTowards(combatTarget.body.position)
        } else if (physics.speed().raw > CORRECTION_EPSILON) {
            kotlin.math.atan2(
                physics.velocity.y.raw,
                physics.velocity.x.raw,
            ).rad
        } else {
            physics.decelerateAngular(ANGULAR_DRAG, deltaMs)
            return
        }

        val rawDelta = desiredAngle - facing
        val angleDelta = normalizeAngle(rawDelta)
        val absAngle = kotlin.math.abs(angleDelta.normalized.let {
            if (it > kotlin.math.PI.toFloat()) (2.0 * kotlin.math.PI).toFloat() - it
            else it
        })
        applyRotationToward(movementConfig, physics, angleDelta, absAngle, deltaMs)
    }

    private fun applyRotationToward(
        movementConfig: MovementConfig,
        physics: ShipPhysics,
        angleDelta: AngleRadians,
        absAngle: Float,
        deltaMs: Int,
    ) {
        if (absAngle < ANGULAR_ARRIVAL_THRESHOLD) {
            physics.decelerateAngular(movementConfig.angularThrust, deltaMs)
            rotationPid.reset()
            return
        }

        val dt = deltaMs * 0.001f
        val normalized = angleDelta.normalized
        val signedError = if (normalized > kotlin.math.PI.toFloat()) {
            normalized - (2.0 * kotlin.math.PI).toFloat()
        } else {
            normalized
        }

        val pidOutput = rotationPid.update(signedError, dt)
        val torqueScale = pidOutput.coerceIn(-1f, 1f)
        physics.applyAngularForce(torqueScale * movementConfig.angularThrust)
    }

    // -- Braking --

    private fun applyCurrentFacingBrake(
        movementConfig: MovementConfig,
        physics: ShipPhysics,
        forwardComponent: Float,
        lateralComponent: Float,
        facing: AngleRadians,
    ) {
        if (forwardComponent < -CORRECTION_EPSILON) {
            physics.applyThrust(
                SceneOffset((-1f).sceneUnit, 0f.sceneUnit),
                movementConfig.reverseThrust,
                facing,
            )
        } else if (forwardComponent > CORRECTION_EPSILON) {
            physics.applyThrust(
                SceneOffset(1f.sceneUnit, 0f.sceneUnit),
                movementConfig.forwardThrust,
                facing,
            )
        }
        if (kotlin.math.abs(lateralComponent) > CORRECTION_EPSILON &&
            movementConfig.lateralThrust > 0f
        ) {
            val lateralSign = if (lateralComponent > 0f) 1f else -1f
            physics.applyThrust(
                SceneOffset(0f.sceneUnit, lateralSign.sceneUnit),
                movementConfig.lateralThrust,
                facing,
            )
        }
    }

    private fun computeBrakingStrategy(
        movementConfig: MovementConfig,
        physics: ShipPhysics,
        speed: Float,
        facing: AngleRadians,
    ): BrakingStrategy {
        if (speed < CORRECTION_EPSILON) {
            return BrakingStrategy(
                stoppingDistance = 0f,
                effectiveDeceleration = 0f,
                shouldRotate = false,
                optimalBrakingAngle = facing,
            )
        }

        val mc = movementConfig
        val vel = physics.velocity

        val antiVelAngle = kotlin.math.atan2(-vel.y.raw, -vel.x.raw)
        val angularAccel = if (mass > 0f) mc.angularThrust / mass else 0f

        data class Candidate(
            val thrustMagnitude: Float,
            val localAngleOffset: Float,
        )

        val candidates = mutableListOf<Candidate>()

        if (mc.forwardThrust > 0f) {
            candidates.add(Candidate(mc.forwardThrust, 0f))
        }
        if (mc.reverseThrust > 0f) {
            candidates.add(Candidate(mc.reverseThrust, kotlin.math.PI.toFloat()))
        }
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

        val facingCos = facing.cos
        val facingSin = facing.sin
        val antiVelX = -vel.x.raw / speed
        val antiVelY = -vel.y.raw / speed
        val fwdDot = facingCos * antiVelX + facingSin * antiVelY
        val revDot = -fwdDot
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

            val rotationTime = if (angularAccel > 0f) {
                2f * kotlin.math.sqrt(angleToRotate / angularAccel)
            } else {
                Float.MAX_VALUE
            }

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

    // -- Angle utilities --

    private fun absAngleOf(angleDelta: AngleRadians): Float {
        val n = angleDelta.normalized
        return if (n > kotlin.math.PI.toFloat()) (2.0 * kotlin.math.PI).toFloat() - n else n
    }

    private fun normalizeAngle(rawDelta: AngleRadians): AngleRadians {
        return if (rawDelta > AngleRadians.Pi) {
            rawDelta - AngleRadians.TwoPi
        } else if (rawDelta < -AngleRadians.Pi) {
            rawDelta + AngleRadians.TwoPi
        } else {
            rawDelta
        }
    }

    companion object {
        private const val ANGULAR_DRAG = 200f
        private const val ARRIVAL_THRESHOLD = 5f
        private const val ANGULAR_ARRIVAL_THRESHOLD = 0.01f
        private const val CORRECTION_EPSILON = 0.1f
        private const val MAX_SPEED_FACTOR = 10f
        private const val BRAKING_MARGIN = 0.8f
        private const val ROTATION_BRAKE_THRESHOLD = 0.75f
        private const val PROXIMITY_RADIUS_FACTOR = 1.5f
        private const val SLOW_SPEED_FACTOR = 1.0f

        private const val ROTATION_PID_KP = 3.0f
        private const val ROTATION_PID_KI = 0.1f
        private const val ROTATION_PID_KD = 2.0f
        private const val ROTATION_PID_INTEGRAL_LIMIT = 2.0f
    }
}
