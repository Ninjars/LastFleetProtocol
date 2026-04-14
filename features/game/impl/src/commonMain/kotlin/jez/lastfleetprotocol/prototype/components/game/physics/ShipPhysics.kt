package jez.lastfleetprotocol.prototype.components.game.physics

import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.normalized
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneUnit
import jez.lastfleetprotocol.prototype.components.game.utils.rotate
import jez.lastfleetprotocol.prototype.components.gamecore.data.MovementConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Encapsulates physics state and integration for a ship.
 *
 * Tracks linear velocity and accumulated forces. Uses semi-implicit Euler
 * integration: velocity is updated before position.
 *
 * **Atmospheric model:** Drag is applied as a quadratic force opposing velocity
 * (`F_drag = effectiveDrag · |v|²`), with the effective drag coefficient smoothly
 * interpolated between per-axis values based on the angle between velocity and
 * ship heading. This produces terminal velocity and smooth skid under rotation.
 *
 * **Turn-rate model:** Angular rotation is rate-limited (degrees per second),
 * not momentum-based. No angular velocity, no torque accumulation.
 */
class ShipPhysics(
    val mass: Float,
    initialVelocity: SceneOffset = SceneOffset.Zero,
) {
    init {
        require(mass > 0f) { "Ship mass must be positive, got $mass" }
    }

    var velocity: SceneOffset = initialVelocity

    // Accumulated force in world space
    private var accumulatedForce: SceneOffset = SceneOffset.Zero

    /** Last computed acceleration for debug visualisation. */
    var lastAcceleration: SceneOffset = SceneOffset.Zero
        private set

    /**
     * Apply thrust in a local-space direction, converted to world space via facing angle.
     */
    fun applyThrust(localDirection: SceneOffset, magnitude: Float, facing: AngleRadians) {
        val worldDirection = localDirection.rotate(facing)
        accumulatedForce += worldDirection * magnitude
    }

    /**
     * Apply quadratic drag opposing current velocity.
     *
     * The effective drag coefficient is computed via normalized angular interpolation
     * of the per-axis coefficients based on the angle between velocity and ship heading.
     * Drag force magnitude is `effectiveDrag · |v|²`, capped to prevent velocity reversal.
     *
     * @param movementConfig provides per-axis drag coefficients
     * @param shipRotation current ship facing angle (radians)
     */
    fun applyDrag(movementConfig: MovementConfig, shipRotation: AngleRadians) {
        val speed = velocity.length()
        if (speed.raw <= SPEED_EPSILON) return

        val effectiveDrag = computeEffectiveDrag(movementConfig, shipRotation)
        if (effectiveDrag <= DRAG_EPSILON) return

        // Quadratic drag: F = effectiveDrag · v²
        val dragForceMagnitude = effectiveDrag * speed.raw * speed.raw

        // Cap drag force so it can't reverse velocity within one frame
        // (the integration step will multiply by dt, so we cap the force itself)
        val dragDirection = velocity.normalized()
        accumulatedForce -= dragDirection * dragForceMagnitude
    }

    /**
     * Compute effective drag coefficient via normalized angular interpolation.
     *
     * Blends forward/reverse/lateral drag coefficients based on the angle between
     * the velocity vector and the ship's forward axis. Normalized so that diagonal
     * movement is not penalized by an interpolation artifact.
     */
    private fun computeEffectiveDrag(movementConfig: MovementConfig, shipRotation: AngleRadians): Float {
        val speed = velocity.length().raw
        if (speed <= SPEED_EPSILON) return 0f

        // Angle between velocity and ship forward
        val velAngle = kotlin.math.atan2(velocity.y.raw.toDouble(), velocity.x.raw.toDouble()).toFloat()
        val theta = velAngle - shipRotation.normalized

        val fwdComp = max(0f, cos(theta))
        val revComp = max(0f, -cos(theta))
        val latComp = abs(sin(theta))
        val totalWeight = fwdComp + revComp + latComp

        return if (totalWeight > 0.001f) {
            (movementConfig.forwardDragCoeff * fwdComp +
                    movementConfig.reverseDragCoeff * revComp +
                    movementConfig.lateralDragCoeff * latComp) / totalWeight
        } else {
            movementConfig.forwardDragCoeff // fallback
        }
    }

    /**
     * Semi-implicit Euler integration for linear motion.
     *
     * Updates velocity from accumulated forces, then computes position delta.
     * Clears accumulated forces after integration.
     *
     * Rotation is handled separately via the turn-rate model (not physics-integrated).
     */
    fun integrate(deltaMs: Int): PhysicsResult {
        val dt = deltaMs * MS_TO_SECONDS

        // Linear: a = F/m, v += a*dt, dx = v*dt
        val acceleration = accumulatedForce * (1f / mass)
        lastAcceleration = acceleration
        velocity += acceleration * dt

        val positionDelta = velocity * dt

        // Clear accumulated forces
        accumulatedForce = SceneOffset.Zero

        return PhysicsResult(positionDelta)
    }

    /**
     * Current speed (magnitude of velocity).
     */
    fun speed(): SceneUnit = velocity.length()

    companion object {
        const val MS_TO_SECONDS = 0.001f
        const val SPEED_EPSILON = 0.01f
        private const val DRAG_EPSILON = 0.0001f
    }
}

data class PhysicsResult(
    val positionDelta: SceneOffset,
)
