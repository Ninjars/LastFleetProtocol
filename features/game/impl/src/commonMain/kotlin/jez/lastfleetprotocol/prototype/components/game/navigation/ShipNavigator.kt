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
import kotlin.math.ln
import kotlin.math.sqrt

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

        // Cruise: aim at the destination *from the ship's projected position*
        // [COURSE_CORRECT_LOOKAHEAD] seconds ahead at current velocity, not from
        // the ship's current position. When velocity points at the destination
        // this collapses to plain destination-direction; when there's a lateral
        // component, the aim tilts so forward thrust contributes a component
        // opposing the lateral drift. Without this correction, lateral velocity
        // carries the hull past the destination while the rotation lags behind,
        // producing a visible orbit around the position marker.
        val velX = physics.velocity.x.raw
        val velY = physics.velocity.y.raw
        val lookaheadX = body.position.x.raw + velX * COURSE_CORRECT_LOOKAHEAD
        val lookaheadY = body.position.y.raw + velY * COURSE_CORRECT_LOOKAHEAD
        val correctedDx = destination.x.raw - lookaheadX
        val correctedDy = destination.y.raw - lookaheadY
        val correctedLen = sqrt(correctedDx * correctedDx + correctedDy * correctedDy)

        val cruiseAngle: AngleRadians
        val cruiseDirX: Float
        val cruiseDirY: Float
        if (correctedLen > CORRECTION_EPSILON) {
            cruiseAngle = kotlin.math.atan2(correctedDy, correctedDx).rad
            cruiseDirX = correctedDx / correctedLen
            cruiseDirY = correctedDy / correctedLen
        } else {
            // Predicted future position lies on the destination — use the
            // uncorrected direction to avoid an undefined aim.
            cruiseAngle = targetAngle
            cruiseDirX = toTarget.x.raw / distanceToTarget
            cruiseDirY = toTarget.y.raw / distanceToTarget
        }

        rotateToward(body, cruiseAngle, turnRate, dt)

        val facingCos = facing.cos
        val facingSin = facing.sin
        val forwardComponent = cruiseDirX * facingCos + cruiseDirY * facingSin
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
     * Apply anti-velocity thrust on the ship's forward axis only — forward
     * thrust when anti-velocity points forward, reverse thrust when it points
     * backward, nothing when it's near-perpendicular. Stronger than the prior
     * 30%-factor brake (cruiser drag at tuned `dragCoeff` is essentially zero
     * at cruise speeds, so brake thrust is the *only* thing actually
     * decelerating the ship), but deliberately does not also fire on the
     * lateral axis.
     *
     * The earlier all-axis variant gave more total brake force but combined
     * `forwardThrust * fwdDot * facing` with `lateralThrust * latDot * left`,
     * which — because `forwardThrust ≠ lateralThrust` — produced a thrust
     * direction up to ~20° off true anti-velocity. As the hull rotated
     * during brake, that off-axis component swept around in a way that
     * showed up as frame-to-frame jitter in the target's acceleration
     * estimate, which the constant-acceleration lead-aim model then
     * amplified at `0.5·a·t²` into visible aim-point wobble. Forward-only
     * brake stays within a single ship-local axis — the world-frame thrust
     * direction stays exactly along that axis as the ship rotates, so the
     * target's measured acceleration stays clean.
     *
     * Tradeoff: when the ship is rotating mid-brake and velocity ends up
     * roughly perpendicular to facing, neither branch fires and the ship
     * coasts on residual sideways motion until rotation realigns. In
     * practice that window is brief — the bumped turn rate finishes most
     * realignments in a few seconds, and brake mode normally engages with
     * the ship already pointed at the destination.
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
         * Cruise aim is computed against the ship's projected position this
         * many seconds ahead at current velocity. Sized to roughly the
         * ~45° hull-rotation time at the cruiser's bumped turn rate (~22°/s);
         * smaller values under-correct and the orbit-around-destination
         * symptom returns, larger values over-correct at close range and can
         * flip the aim past the destination on slow approaches.
         */
        private const val COURSE_CORRECT_LOOKAHEAD = 2f

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
