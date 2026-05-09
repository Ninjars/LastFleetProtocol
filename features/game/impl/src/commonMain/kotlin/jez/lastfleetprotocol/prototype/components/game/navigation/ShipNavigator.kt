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

/**
 * Resolves navigation for a ship under the atmospheric drag model.
 *
 * Three distinct phases, picked per-frame from current state:
 * - **Cruise:** rotate hull toward destination, apply forward thrust scaled by
 *   alignment. Lateral and reverse thrusters stay quiet here — using them
 *   diverts the resultant thrust off the destination axis and produces curved
 *   approach paths. With the bumped turn rate the rotation completes in a few
 *   seconds, so the ship spends almost all its travel time aligned and
 *   accelerating straight at the destination.
 * - **Brake:** apply full anti-velocity thrust on whichever ship-local axes
 *   match (forward / reverse / lateral). Engaged whenever the realistic
 *   stopping distance — computed against `reverseThrust`, since that's what
 *   the navigator actually uses to brake — exceeds the remaining travel.
 * - **Arrived:** hold near destination. Cleared once we're inside the arrival
 *   threshold *and* slow enough that residual brake bleeds the rest off.
 */
class ShipNavigator {
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
            applyBrakeThrust(physics, movementConfig, facing, speed)
            rotateToCombatTarget(body, physics, combatTarget, turnRate, dt)
            return null
        }

        val toTarget = destination - body.position
        val distanceToTarget = toTarget.length().raw
        val targetAngle = kotlin.math.atan2(toTarget.y.raw, toTarget.x.raw).rad

        // Truly arrived: near destination AND slow enough for residual brake to
        // settle the rest. Clears destination so the AI / player can issue a new
        // one without the navigator continuing to brake against an old target.
        if (distanceToTarget < ARRIVAL_THRESHOLD && speed < ARRIVAL_SPEED) {
            applyBrakeThrust(physics, movementConfig, facing, speed)
            rotateToCombatTarget(body, physics, combatTarget, turnRate, dt)
            return null
        }

        // Realistic brake estimate: reverseThrust is what `applyBrakeThrust`
        // actually uses when the ship faces the destination (the common case).
        // Using `forwardThrust` here — as the prior code did — over-promised
        // braking by ~2.4× on the standard cruiser, so the navigator started
        // braking too late and the ship sailed straight through arrival.
        val brakeThrust = movementConfig.reverseThrust.coerceAtLeast(MIN_BRAKE_THRUST)
        val stoppingDist = stoppingDistanceUnderDrag(speed, brakeThrust, movementConfig.forwardDragCoeff, physics.mass)
        val isBraking = stoppingDist >= distanceToTarget * BRAKING_MARGIN

        // Inside arrival threshold but still moving fast, OR outside but stopping
        // distance has caught up: brake hard, keep rotating toward destination so
        // reverse thrust stays the brake axis.
        if (isBraking || distanceToTarget < ARRIVAL_THRESHOLD) {
            applyBrakeThrust(physics, movementConfig, facing, speed)
            rotateToward(body, targetAngle, turnRate, dt)
            return destination
        }

        // Cruise: rotate hull toward destination and apply forward thrust scaled
        // by alignment. Lateral and reverse stay off — see class KDoc for why.
        rotateToward(body, targetAngle, turnRate, dt)
        val facingCos = facing.cos
        val facingSin = facing.sin
        val forwardComponent = (toTarget.x.raw * facingCos + toTarget.y.raw * facingSin) / distanceToTarget
        if (forwardComponent > THRUST_ALIGNMENT_THRESHOLD) {
            physics.applyThrust(
                SceneOffset(1f.sceneUnit, 0f.sceneUnit),
                movementConfig.forwardThrust * forwardComponent,
                facing,
            )
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

    /**
     * Apply full anti-velocity thrust on whichever ship-local axes are aligned
     * with `-velocity`. Decomposes anti-velocity into forward / lateral
     * components in the ship's frame and fires the matching thrusters
     * (`forwardThrust`, `reverseThrust`, or `lateralThrust`) at a magnitude
     * scaled by alignment. Replaces the prior 30%-factor low-speed brake,
     * which was the proximate cause of "ships don't stop" — quadratic drag at
     * the cruiser's tuned `dragCoeff` is essentially zero at cruise speeds, so
     * the brake thrust is the only thing actually decelerating the ship. At
     * 30% it couldn't keep up with the navigator's cruise output.
     *
     * Lateral thrust does fire here even though it's the weakest axis — when
     * the ship is rotating mid-brake, velocity becomes lateral relative to
     * facing, and the previous "no lateral brake" choice meant the ship
     * coasted on residual sideways motion that drag couldn't bleed.
     */
    private fun applyBrakeThrust(
        physics: ShipPhysics,
        movementConfig: MovementConfig,
        facing: AngleRadians,
        speed: Float,
    ) {
        if (speed < CORRECTION_EPSILON) return

        val facingCos = facing.cos
        val facingSin = facing.sin
        val antiVelX = -physics.velocity.x.raw / speed
        val antiVelY = -physics.velocity.y.raw / speed
        val fwdAxisDot = facingCos * antiVelX + facingSin * antiVelY
        val latAxisDot = -facingSin * antiVelX + facingCos * antiVelY

        if (fwdAxisDot > THRUST_ALIGNMENT_THRESHOLD) {
            physics.applyThrust(
                SceneOffset(1f.sceneUnit, 0f.sceneUnit),
                movementConfig.forwardThrust * fwdAxisDot,
                facing,
            )
        } else if (fwdAxisDot < -THRUST_ALIGNMENT_THRESHOLD) {
            physics.applyThrust(
                SceneOffset((-1f).sceneUnit, 0f.sceneUnit),
                movementConfig.reverseThrust * (-fwdAxisDot),
                facing,
            )
        }

        if (movementConfig.lateralThrust > 0f && abs(latAxisDot) > THRUST_ALIGNMENT_THRESHOLD) {
            val latSign = if (latAxisDot > 0f) 1f else -1f
            physics.applyThrust(
                SceneOffset(0f.sceneUnit, latSign.sceneUnit),
                movementConfig.lateralThrust * abs(latAxisDot),
                facing,
            )
        }
    }

    // -- Utilities --

    private fun computeTurnRate(movementConfig: MovementConfig, mass: Float): Float {
        if (mass <= 0f) return 0f
        return movementConfig.angularThrust / mass * TURN_RATE_SCALE
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

        /**
         * Lower bound on the brake-thrust used in the stopping-distance
         * estimate, to avoid divide-by-zero / huge stopping distance when a
         * design has no reverse thruster. 1 N is purely defensive — real
         * configs have several kN reverse.
         */
        private const val MIN_BRAKE_THRUST = 1f

        private const val CORRECTION_EPSILON = 0.1f
        private const val DRAG_EPSILON = 0.0001f

        /**
         * Safety margin on the brake decision — engage braking when stopping
         * distance reaches 80% of remaining distance, not 100%. Buys the
         * navigator a slack frame for the brake to ramp up.
         */
        private const val BRAKING_MARGIN = 0.8f

        private const val THRUST_ALIGNMENT_THRESHOLD = 0.1f

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
