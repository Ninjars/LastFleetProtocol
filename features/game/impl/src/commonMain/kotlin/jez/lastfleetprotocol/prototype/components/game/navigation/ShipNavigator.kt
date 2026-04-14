package jez.lastfleetprotocol.prototype.components.game.navigation

import com.pandulapeter.kubriko.actor.body.BoxBody
import com.pandulapeter.kubriko.helpers.extensions.angleTowards
import com.pandulapeter.kubriko.helpers.extensions.cos
import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.helpers.extensions.sin
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import jez.lastfleetprotocol.prototype.components.game.actors.Targetable
import jez.lastfleetprotocol.prototype.components.game.physics.ShipPhysics
import jez.lastfleetprotocol.prototype.components.gamecore.data.MovementConfig
import jez.lastfleetprotocol.prototype.components.gamecore.stats.RHO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Resolves navigation for a ship under the atmospheric drag model:
 * - Thrust toward destination, drag always active
 * - Turn-rate rotation (degrees per second, not momentum-based)
 * - Drag-aware braking distance using closed-form stopping formula
 * - Terminal velocity cap replaces the old MAX_SPEED_FACTOR
 *
 * @param hullRadius average distance from ship centre to hull vertices
 */
class ShipNavigator(
    private val hullRadius: Float,
) {
    /**
     * Navigate the ship: apply thrust, apply drag, rotate toward target, brake when arriving.
     *
     * @return updated destination (null if arrived)
     */
    fun navigate(
        movementConfig: MovementConfig,
        physics: ShipPhysics,
        body: BoxBody,
        combatTarget: Targetable?,
        destination: SceneOffset?,
        deltaMs: Int,
    ): SceneOffset? {
        val dt = deltaMs * 0.001f
        val facing = body.rotation
        val speed = physics.speed().raw
        val turnRate = computeTurnRate(movementConfig, physics.mass)
        val terminalVel = terminalVelocity(movementConfig.forwardThrust, movementConfig.forwardDragCoeff)

        // Drag is ALWAYS active — this is the atmospheric model
        physics.applyDrag(movementConfig, facing)

        if (destination == null) {
            // No destination: apply low-speed braking thrust if moving, rotate to combat target
            applyLowSpeedBrake(physics, movementConfig, facing, speed)
            rotateToCombatTarget(body, physics, combatTarget, turnRate, dt)
            return null
        }

        val toTarget = destination - body.position
        val distanceToTarget = toTarget.length().raw

        // Snap-to-stop: close to destination and moving slowly
        if (distanceToTarget < hullRadius * PROXIMITY_RADIUS_FACTOR && speed < hullRadius * SLOW_SPEED_FACTOR) {
            applyLowSpeedBrake(physics, movementConfig, facing, speed)
            rotateToCombatTarget(body, physics, combatTarget, turnRate, dt)
            return null
        }

        // Hard arrival
        if (distanceToTarget < ARRIVAL_THRESHOLD) {
            applyLowSpeedBrake(physics, movementConfig, facing, speed)
            rotateToCombatTarget(body, physics, combatTarget, turnRate, dt)
            return null
        }

        // Compute braking distance under drag
        val effectiveDrag = movementConfig.forwardDragCoeff
        val brakingThrust = movementConfig.forwardThrust
        val stoppingDist = stoppingDistanceUnderDrag(speed, brakingThrust, effectiveDrag, physics.mass)
        val isBraking = stoppingDist >= distanceToTarget * BRAKING_MARGIN

        val targetDirection = toTarget * (1f / distanceToTarget)

        val desiredSpeed = if (isBraking) {
            speedForBrakingDistance(distanceToTarget, brakingThrust, effectiveDrag, physics.mass)
                .coerceAtMost(terminalVel)
        } else {
            terminalVel
        }

        val desiredVelocity = targetDirection * desiredSpeed
        val correction = desiredVelocity - physics.velocity
        val correctionMag = correction.length().raw

        if (correctionMag < CORRECTION_EPSILON) {
            rotateToCombatTarget(body, physics, combatTarget, turnRate, dt)
            return destination
        }

        // Decompose correction into forward/lateral relative to facing
        val corrX = correction.x.raw
        val corrY = correction.y.raw
        val facingCos = facing.cos
        val facingSin = facing.sin
        val forwardComponent = corrX * facingCos + corrY * facingSin
        val lateralComponent = -corrX * facingSin + corrY * facingCos

        // Apply thrust
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

        if (abs(lateralComponent) > CORRECTION_EPSILON && movementConfig.lateralThrust > 0f) {
            val lateralSign = if (lateralComponent > 0f) 1f else -1f
            physics.applyThrust(
                SceneOffset(0f.sceneUnit, lateralSign.sceneUnit),
                movementConfig.lateralThrust,
                facing,
            )
        }

        // Rotation: face correction direction or combat target
        if (isBraking || correctionMag < terminalVel * 0.3f) {
            rotateToCombatTarget(body, physics, combatTarget, turnRate, dt)
        } else {
            val correctionAngle = kotlin.math.atan2(corrY, corrX).rad
            rotateToward(body, correctionAngle, turnRate, dt)
        }

        return destination
    }

    // -- Turn-rate rotation --

    private fun rotateToCombatTarget(
        body: BoxBody,
        physics: ShipPhysics,
        combatTarget: Targetable?,
        turnRate: Float,
        dt: Float,
    ) {
        val desiredAngle = if (combatTarget != null && combatTarget.isValidTarget()) {
            body.position.angleTowards(combatTarget.body.position)
        } else if (physics.speed().raw > CORRECTION_EPSILON) {
            kotlin.math.atan2(physics.velocity.y.raw, physics.velocity.x.raw).rad
        } else {
            return // No target, not moving — don't rotate
        }

        rotateToward(body, desiredAngle, turnRate, dt)
    }

    /**
     * Rate-limited rotation toward a target angle.
     * Rotates body by at most turnRate * dt degrees, taking the shortest path.
     */
    private fun rotateToward(body: BoxBody, targetAngle: AngleRadians, turnRate: Float, dt: Float) {
        val turnRateRad = turnRate * (PI.toFloat() / 180f) // deg/sec → rad/sec
        val maxRotation = turnRateRad * dt

        val rawDelta = targetAngle - body.rotation
        val delta = normalizeAngle(rawDelta).normalized.let {
            if (it > PI.toFloat()) it - (2.0 * PI).toFloat() else it
        }

        val clamped = delta.coerceIn(-maxRotation, maxRotation)
        body.rotation += clamped.rad
    }

    // -- Drag-aware braking math --

    /**
     * Stopping distance under quadratic drag with active braking thrust.
     * Formula: x = (m / 2k) · ln((F + k·v²) / F)
     * where F = braking thrust, k = drag coefficient, v = current speed, m = mass.
     *
     * Falls back to frictionless v²/(2a) when drag is near zero.
     */
    private fun stoppingDistanceUnderDrag(speed: Float, thrust: Float, dragCoeff: Float, mass: Float): Float {
        if (speed < CORRECTION_EPSILON) return 0f
        if (thrust <= 0f) return Float.MAX_VALUE

        if (dragCoeff < DRAG_EPSILON) {
            // Frictionless fallback: v²/(2a) where a = thrust/mass
            val decel = thrust / mass
            return if (decel > 0f) (speed * speed) / (2f * decel) else Float.MAX_VALUE
        }

        // Drag-aware: x = (m / 2k) · ln((F + k·v²) / F)
        val kv2 = dragCoeff * speed * speed
        return (mass / (2f * dragCoeff)) * ln((thrust + kv2) / thrust)
    }

    /**
     * Maximum speed for a given braking distance.
     * Inverse of stopping distance: v = sqrt((F/k) · (exp(2k·x/m) - 1))
     * Clamped to terminal velocity.
     */
    private fun speedForBrakingDistance(distance: Float, thrust: Float, dragCoeff: Float, mass: Float): Float {
        if (distance <= 0f) return 0f
        if (thrust <= 0f) return 0f

        if (dragCoeff < DRAG_EPSILON) {
            // Frictionless fallback: v = sqrt(2·a·d)
            val decel = thrust / mass
            return sqrt(2f * decel * distance)
        }

        val exponent = (2f * dragCoeff * distance / mass).coerceAtMost(20f) // cap to prevent overflow
        return sqrt((thrust / dragCoeff) * (kotlin.math.exp(exponent) - 1f))
    }

    /**
     * At low speed near a destination, quadratic drag vanishes. Apply a small
     * braking thrust opposing velocity to bring the ship to rest.
     */
    private fun applyLowSpeedBrake(
        physics: ShipPhysics,
        movementConfig: MovementConfig,
        facing: AngleRadians,
        speed: Float,
    ) {
        if (speed < CORRECTION_EPSILON) return

        // Apply reverse thrust in the velocity direction to brake
        val velAngle = kotlin.math.atan2(physics.velocity.y.raw, physics.velocity.x.raw).rad
        val facingCos = facing.cos
        val facingSin = facing.sin
        val antiVelX = -physics.velocity.x.raw / speed
        val antiVelY = -physics.velocity.y.raw / speed
        val fwdDot = facingCos * antiVelX + facingSin * antiVelY

        if (fwdDot > 0.5f) {
            // Anti-velocity is roughly forward — apply forward thrust to brake
            physics.applyThrust(
                SceneOffset(1f.sceneUnit, 0f.sceneUnit),
                movementConfig.forwardThrust * LOW_SPEED_BRAKE_FACTOR,
                facing,
            )
        } else if (fwdDot < -0.5f) {
            // Anti-velocity is roughly reverse
            physics.applyThrust(
                SceneOffset((-1f).sceneUnit, 0f.sceneUnit),
                movementConfig.reverseThrust * LOW_SPEED_BRAKE_FACTOR,
                facing,
            )
        }
    }

    // -- Utilities --

    private fun computeTurnRate(movementConfig: MovementConfig, mass: Float): Float {
        if (mass <= 0f) return 0f
        return movementConfig.angularThrust / mass * TURN_RATE_SCALE
    }

    private fun terminalVelocity(thrust: Float, dragCoeff: Float): Float {
        if (dragCoeff <= 0f) return thrust / 10f * MAX_SPEED_FALLBACK_FACTOR // frictionless fallback
        return sqrt(thrust / dragCoeff)
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
        private const val ARRIVAL_THRESHOLD = 5f
        private const val CORRECTION_EPSILON = 0.1f
        private const val DRAG_EPSILON = 0.0001f
        private const val BRAKING_MARGIN = 0.8f
        private const val PROXIMITY_RADIUS_FACTOR = 1.5f
        private const val SLOW_SPEED_FACTOR = 1.0f
        private const val LOW_SPEED_BRAKE_FACTOR = 0.3f
        private const val MAX_SPEED_FALLBACK_FACTOR = 10f // for zero-drag ships
        private const val TURN_RATE_SCALE = 50f // matches ShipStatsCore
    }
}
