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

/**
 * Resolves navigation for a ship under the atmospheric drag model.
 *
 * The navigator no longer switches between hand-authored cruise/brake phases.
 * It computes a desired velocity for the current position error, scores a small
 * set of candidate headings using [ShipMotionModel], then rotates and thrusts
 * toward the heading that best reduces velocity error under drag. This lets the
 * ship deliberately turn broadside to its velocity when lateral drag is the best
 * way to scrub sideways motion, while still preserving momentum and mass.
 */
class ShipNavigator(
    private val motionModel: ShipMotionModel = ShipMotionModel(),
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
            holdPosition(
                movementConfig = movementConfig,
                physics = physics,
                body = body,
                combatTarget = combatTarget,
                turnRate = turnRate,
                dt = dt,
            )
            return null
        }

        val toTarget = destination - body.position
        val distanceToTarget = toTarget.length().raw

        // Truly arrived: close and slow. Until both are true, keep planning a
        // zero/near-zero desired velocity instead of entering a separate brake
        // mode, so residual sideways motion is handled by the same guidance law.
        if (distanceToTarget < ARRIVAL_THRESHOLD && speed < ARRIVAL_SPEED) {
            applyUsefulThrust(
                physics = physics,
                movementConfig = movementConfig,
                heading = facing,
                desiredVelocity = SceneOffset.Zero,
            )
            rotateToCombatTarget(body, physics, combatTarget, turnRate, dt)
            return null
        }

        val plan = motionModel.velocityPlan(
            position = body.position,
            destination = destination,
            velocity = physics.velocity,
            movementConfig = movementConfig,
            mass = physics.mass,
        )
        val selectedHeading = selectHeading(
            movementConfig = movementConfig,
            physics = physics,
            currentHeading = facing,
            plan = plan,
            dt = dt,
        )

        rotateToward(body, selectedHeading, turnRate, dt)
        applyUsefulThrust(
            physics = physics,
            movementConfig = movementConfig,
            heading = body.rotation,
            desiredVelocity = plan.desiredVelocity,
        )

        return destination
    }

    private fun holdPosition(
        movementConfig: MovementConfig,
        physics: ShipPhysics,
        body: BoxBody,
        combatTarget: Targetable?,
        turnRate: Float,
        dt: Float,
    ) {
        if (combatTarget != null && combatTarget.isValidTarget()) {
            rotateToCombatTarget(body, physics, combatTarget, turnRate, dt)
            applyUsefulThrust(
                physics = physics,
                movementConfig = movementConfig,
                heading = body.rotation,
                desiredVelocity = SceneOffset.Zero,
            )
            return
        }

        if (physics.speed().raw > CORRECTION_EPSILON) {
            val stopPlan = ShipMotionModel.VelocityPlan(
                desiredVelocity = SceneOffset.Zero,
                targetDirection = physics.velocity * -1f * (1f / physics.speed().raw),
                distance = 0f,
                lateralVelocity = SceneOffset.Zero,
            )
            val selectedHeading = selectHeading(
                movementConfig = movementConfig,
                physics = physics,
                currentHeading = body.rotation,
                plan = stopPlan,
                dt = dt,
            )
            rotateToward(body, selectedHeading, turnRate, dt)
        }
        applyUsefulThrust(
            physics = physics,
            movementConfig = movementConfig,
            heading = body.rotation,
            desiredVelocity = SceneOffset.Zero,
        )
    }

    private fun selectHeading(
        movementConfig: MovementConfig,
        physics: ShipPhysics,
        currentHeading: AngleRadians,
        plan: ShipMotionModel.VelocityPlan,
        dt: Float,
    ): AngleRadians {
        var bestHeading = currentHeading
        var bestScore = Float.NEGATIVE_INFINITY

        for (candidate in motionModel.candidateHeadings(
            currentHeading = currentHeading,
            targetDirection = plan.targetDirection,
            velocity = physics.velocity,
            desiredVelocity = plan.desiredVelocity,
        )) {
            val score = motionModel.scoreHeading(
                heading = candidate,
                currentHeading = currentHeading,
                velocity = physics.velocity,
                desiredVelocity = plan.desiredVelocity,
                targetDirection = plan.targetDirection,
                lateralVelocity = plan.lateralVelocity,
                movementConfig = movementConfig,
                mass = physics.mass,
                dt = dt.coerceAtLeast(MIN_SCORE_DT),
            )
            if (score > bestScore) {
                bestScore = score
                bestHeading = candidate
            }
        }

        return bestHeading
    }

    private fun applyUsefulThrust(
        physics: ShipPhysics,
        movementConfig: MovementConfig,
        heading: AngleRadians,
        desiredVelocity: SceneOffset,
    ) {
        val velocityError = desiredVelocity - physics.velocity
        val errorSpeed = velocityError.length().raw
        if (errorSpeed < CORRECTION_EPSILON) return

        val desiredAccelX = velocityError.x.raw / errorSpeed
        val desiredAccelY = velocityError.y.raw / errorSpeed
        val facingCos = heading.cos
        val facingSin = heading.sin
        val forwardDot = desiredAccelX * facingCos + desiredAccelY * facingSin
        val lateralDot = -desiredAccelX * facingSin + desiredAccelY * facingCos

        if (forwardDot > THRUST_ALIGNMENT_THRESHOLD) {
            physics.applyThrust(
                SceneOffset(1f.sceneUnit, 0f.sceneUnit),
                movementConfig.forwardThrust * forwardDot,
                heading,
            )
        } else if (forwardDot < -THRUST_ALIGNMENT_THRESHOLD) {
            physics.applyThrust(
                SceneOffset((-1f).sceneUnit, 0f.sceneUnit),
                movementConfig.reverseThrust * (-forwardDot),
                heading,
            )
        }

        if (abs(lateralDot) > LATERAL_THRUST_ALIGNMENT_THRESHOLD && movementConfig.lateralThrust > 0f) {
            val lateralSign = if (lateralDot > 0f) 1f else -1f
            physics.applyThrust(
                SceneOffset(0f.sceneUnit, lateralSign.sceneUnit),
                movementConfig.lateralThrust * ShipMotionModel.LATERAL_CORRECTION_THRUST_FRACTION * abs(lateralDot),
                heading,
            )
        }
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

    private fun rotateToward(
        body: BoxBody,
        targetAngle: AngleRadians,
        turnRate: Float,
        dt: Float,
    ) {
        val turnRateRad = turnRate * (PI.toFloat() / 180f)
        val maxRotation = turnRateRad * dt

        val rawDelta = targetAngle - body.rotation
        val delta = normalizeAngle(rawDelta).normalized.let {
            if (it > PI.toFloat()) it - (2.0 * PI).toFloat() else it
        }

        val clamped = delta.coerceIn(-maxRotation, maxRotation)
        body.rotation += clamped.rad
    }

    // -- Utilities --

    private fun computeTurnRate(
        movementConfig: MovementConfig,
        mass: Float,
    ): Float {
        if (mass <= 0f) return 0f
        return movementConfig.angularThrust / mass * TURN_RATE_SCALE
    }

    private fun normalizeAngle(
        rawDelta: AngleRadians,
    ): AngleRadians = if (rawDelta > AngleRadians.Pi) {
        rawDelta - AngleRadians.TwoPi
    } else if (rawDelta < -AngleRadians.Pi) {
        rawDelta + AngleRadians.TwoPi
    } else {
        rawDelta
    }

    companion object {
        /**
         * Arrival tolerance — the radius around the destination at which we
         * declare the ship "close enough" and stop counting position error.
         * 50m for cruiser-class; smaller hulls would want it tighter, larger
         * hulls looser, but no consumer needs class-specific tuning yet.
         */
        private const val ARRIVAL_THRESHOLD = 50f

        /**
         * Speed (m/s) below which the ship is considered "settled" inside the
         * arrival threshold and the destination clears. Tuned so that at this
         * speed, brake thrust + drag bleed the residual motion within a few
         * frames — picking too high a value lets the ship drift clean through
         * arrival, too low keeps the ship in brake-mode forever for AI
         * destinations that drift slightly each frame.
         */
        private const val ARRIVAL_SPEED = 2f

        private const val CORRECTION_EPSILON = 0.1f
        private const val MIN_SCORE_DT = 0.016f
        private const val THRUST_ALIGNMENT_THRESHOLD = 0.1f
        private const val LATERAL_THRUST_ALIGNMENT_THRESHOLD = 0.28f

        /**
         * Multiplier on `angularThrust / mass` to produce a turn rate in
         * degrees-per-second. Bumped from 50 (~4°/s on the standard cruiser —
         * a 90° turn took 21s, sluggish for combat) to 250 (~22°/s, ~4s for
         * 90°). The formula has no physical units, so the constant is purely
         * a feel-tuning lever; cruisers stay clearly slower than corvettes
         * via their lower angularThrust:mass ratio.
         */
        private const val TURN_RATE_SCALE = 250f
    }
}
