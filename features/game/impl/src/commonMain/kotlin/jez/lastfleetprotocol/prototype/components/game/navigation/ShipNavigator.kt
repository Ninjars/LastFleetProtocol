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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Resolves navigation for a ship under the atmospheric drag model.
 *
 * Key principle: thrust is applied toward the destination and drag naturally
 * caps speed at terminal velocity. The navigator does NOT try to match a precise
 * desired velocity — that causes oscillation under drag. Instead:
 * - **Cruising:** apply forward thrust toward target. Drag caps speed.
 * - **Braking:** stop thrusting. Drag + optional braking thrust decelerates.
 * - **Rotation:** rate-limited turn toward heading target (degrees/sec).
 */
class ShipNavigator(
    private val hullRadius: Float,
) {
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

        // Drag is ALWAYS active
        physics.applyDrag(movementConfig, facing)

        if (destination == null) {
            applyLowSpeedBrake(physics, movementConfig, facing, speed)
            rotateToCombatTarget(body, physics, combatTarget, turnRate, dt)
            return null
        }

        val toTarget = destination - body.position
        val distanceToTarget = toTarget.length().raw

        // Snap-to-stop: close and slow
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

        // Should we be braking?
        val effectiveDrag = movementConfig.forwardDragCoeff
        val stoppingDist = stoppingDistanceUnderDrag(speed, movementConfig.forwardThrust, effectiveDrag, physics.mass)
        val isBraking = stoppingDist >= distanceToTarget * BRAKING_MARGIN

        if (isBraking) {
            // Braking: don't apply forward thrust. Drag decelerates naturally.
            // Apply low-speed brake for the final approach where drag vanishes.
            applyLowSpeedBrake(physics, movementConfig, facing, speed)
            rotateToCombatTarget(body, physics, combatTarget, turnRate, dt)
            return destination
        }

        // Cruising: apply thrust toward the destination. Drag caps speed at terminal velocity.
        // Decompose the target direction into ship-local axes and apply appropriate thrust.
        val targetAngle = kotlin.math.atan2(toTarget.y.raw, toTarget.x.raw).rad
        val facingCos = facing.cos
        val facingSin = facing.sin
        val targetDirX = toTarget.x.raw / distanceToTarget
        val targetDirY = toTarget.y.raw / distanceToTarget
        val forwardComponent = targetDirX * facingCos + targetDirY * facingSin
        val lateralComponent = -targetDirX * facingSin + targetDirY * facingCos

        // Apply thrust proportional to the alignment with each axis.
        // Full thrust when well-aligned, reduced when at an angle.
        if (forwardComponent > THRUST_ALIGNMENT_THRESHOLD) {
            physics.applyThrust(
                SceneOffset(1f.sceneUnit, 0f.sceneUnit),
                movementConfig.forwardThrust * forwardComponent,
                facing,
            )
        } else if (forwardComponent < -THRUST_ALIGNMENT_THRESHOLD) {
            physics.applyThrust(
                SceneOffset((-1f).sceneUnit, 0f.sceneUnit),
                movementConfig.reverseThrust * (-forwardComponent),
                facing,
            )
        }

        if (abs(lateralComponent) > THRUST_ALIGNMENT_THRESHOLD && movementConfig.lateralThrust > 0f) {
            val lateralSign = if (lateralComponent > 0f) 1f else -1f
            physics.applyThrust(
                SceneOffset(0f.sceneUnit, lateralSign.sceneUnit),
                movementConfig.lateralThrust * abs(lateralComponent),
                facing,
            )
        }

        // Rotate: face toward destination when cruising, toward combat target when close
        val terminalVel = terminalVelocity(movementConfig.forwardThrust, movementConfig.forwardDragCoeff)
        if (speed > terminalVel * 0.5f && combatTarget != null) {
            rotateToCombatTarget(body, physics, combatTarget, turnRate, dt)
        } else {
            rotateToward(body, targetAngle, turnRate, dt)
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
            return
        }
        rotateToward(body, desiredAngle, turnRate, dt)
    }

    private fun rotateToward(body: BoxBody, targetAngle: AngleRadians, turnRate: Float, dt: Float) {
        val turnRateRad = turnRate * (PI.toFloat() / 180f)
        val maxRotation = turnRateRad * dt

        val rawDelta = targetAngle - body.rotation
        val delta = normalizeAngle(rawDelta).normalized.let {
            if (it > PI.toFloat()) it - (2.0 * PI).toFloat() else it
        }

        val clamped = delta.coerceIn(-maxRotation, maxRotation)
        body.rotation += clamped.rad
    }

    // -- Drag-aware braking --

    private fun stoppingDistanceUnderDrag(speed: Float, thrust: Float, dragCoeff: Float, mass: Float): Float {
        if (speed < CORRECTION_EPSILON) return 0f
        if (thrust <= 0f) return Float.MAX_VALUE

        if (dragCoeff < DRAG_EPSILON) {
            val decel = thrust / mass
            return if (decel > 0f) (speed * speed) / (2f * decel) else Float.MAX_VALUE
        }

        val kv2 = dragCoeff * speed * speed
        return (mass / (2f * dragCoeff)) * ln((thrust + kv2) / thrust)
    }

    private fun applyLowSpeedBrake(
        physics: ShipPhysics,
        movementConfig: MovementConfig,
        facing: AngleRadians,
        speed: Float,
    ) {
        if (speed < CORRECTION_EPSILON) return

        // Apply thrust opposing velocity to bring ship to rest
        val facingCos = facing.cos
        val facingSin = facing.sin
        val antiVelX = -physics.velocity.x.raw / speed
        val antiVelY = -physics.velocity.y.raw / speed
        val fwdDot = facingCos * antiVelX + facingSin * antiVelY

        if (fwdDot > 0.3f) {
            physics.applyThrust(
                SceneOffset(1f.sceneUnit, 0f.sceneUnit),
                movementConfig.forwardThrust * LOW_SPEED_BRAKE_FACTOR,
                facing,
            )
        } else if (fwdDot < -0.3f) {
            physics.applyThrust(
                SceneOffset((-1f).sceneUnit, 0f.sceneUnit),
                movementConfig.reverseThrust * LOW_SPEED_BRAKE_FACTOR,
                facing,
            )
        }
        // If velocity is roughly perpendicular to facing, lateral thrust would help
        // but for simplicity we let drag handle it
    }

    // -- Utilities --

    private fun computeTurnRate(movementConfig: MovementConfig, mass: Float): Float {
        if (mass <= 0f) return 0f
        return movementConfig.angularThrust / mass * TURN_RATE_SCALE
    }

    private fun terminalVelocity(thrust: Float, dragCoeff: Float): Float {
        if (dragCoeff <= DRAG_EPSILON) return thrust * MAX_SPEED_FALLBACK_FACTOR
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
        private const val MAX_SPEED_FALLBACK_FACTOR = 0.1f
        private const val THRUST_ALIGNMENT_THRESHOLD = 0.1f
        private const val TURN_RATE_SCALE = 50f
    }
}
