package jez.lastfleetprotocol.prototype.components.game.physics

import com.pandulapeter.kubriko.helpers.extensions.length
import com.pandulapeter.kubriko.helpers.extensions.normalized
import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.AngleRadians
import com.pandulapeter.kubriko.types.SceneOffset
import com.pandulapeter.kubriko.types.SceneUnit
import jez.lastfleetprotocol.prototype.components.game.utils.rotate

/**
 * Encapsulates physics state and integration for a ship.
 *
 * Tracks linear velocity, angular velocity, and accumulated forces.
 * Uses semi-implicit Euler integration: velocity is updated before position,
 * which provides better energy conservation than explicit Euler.
 */
class ShipPhysics(
    val mass: Float,
    initialVelocity: SceneOffset = SceneOffset.Zero,
) {
    var velocity: SceneOffset = initialVelocity
    var angularVelocity: Float = 0f

    // Accumulated force in world space (Newtons equivalent)
    private var accumulatedForce: SceneOffset = SceneOffset.Zero
    // Accumulated torque
    private var accumulatedTorque: Float = 0f

    /** Last computed acceleration for debug visualisation. */
    var lastAcceleration: SceneOffset = SceneOffset.Zero
        private set

    /**
     * Apply thrust in a local-space direction, converted to world space via facing angle.
     *
     * @param localDirection direction in ship-local space (e.g., (0, -1) for forward)
     * @param magnitude force magnitude
     * @param facing ship's current facing angle
     */
    fun applyThrust(localDirection: SceneOffset, magnitude: Float, facing: AngleRadians) {
        val worldDirection = localDirection.rotate(facing)
        accumulatedForce += worldDirection * magnitude
    }

    /**
     * Apply angular force (torque) to rotate the ship.
     *
     * @param torque signed torque value (positive = clockwise in screen coords)
     */
    fun applyAngularForce(torque: Float) {
        accumulatedTorque += torque
    }

    /**
     * Apply a drag/braking force opposing current velocity.
     * The drag force is capped so it cannot reverse the velocity direction.
     *
     * @param dragCoefficient force magnitude of drag per unit speed
     * @param deltaMs time step in milliseconds
     */
    fun decelerate(dragCoefficient: Float, deltaMs: Int) {
        val speed = velocity.length()
        if (speed.raw <= SPEED_EPSILON) {
            velocity = SceneOffset.Zero
            return
        }
        val dt = deltaMs * MS_TO_SECONDS
        // Maximum deceleration that would bring velocity to zero this frame
        val maxDecel = speed.raw / dt
        val dragMagnitude = minOf(dragCoefficient, maxDecel)
        val dragDirection = velocity.normalized()
        accumulatedForce -= dragDirection * dragMagnitude
    }

    /**
     * Apply angular drag opposing current angular velocity.
     *
     * @param angularDragCoefficient torque magnitude of drag
     * @param deltaMs time step in milliseconds
     */
    fun decelerateAngular(angularDragCoefficient: Float, deltaMs: Int) {
        if (kotlin.math.abs(angularVelocity) < ANGULAR_EPSILON) {
            angularVelocity = 0f
            return
        }
        val dt = deltaMs * MS_TO_SECONDS
        val maxDecel = kotlin.math.abs(angularVelocity) / dt * mass
        val dragMagnitude = minOf(angularDragCoefficient, maxDecel)
        val sign = if (angularVelocity > 0f) -1f else 1f
        accumulatedTorque += sign * dragMagnitude
    }

    /**
     * Semi-implicit Euler integration.
     *
     * Updates velocity from accumulated forces, then computes position delta from
     * the updated velocity. Clears accumulated forces after integration.
     *
     * @return (positionDelta, rotationDelta) to apply to the ship's body
     */
    fun integrate(deltaMs: Int): PhysicsResult {
        val dt = deltaMs * MS_TO_SECONDS

        // Linear: a = F/m, v += a*dt, dx = v*dt
        val acceleration = accumulatedForce * (1f / mass)
        lastAcceleration = acceleration
        velocity += acceleration * dt

        val positionDelta = velocity * dt

        // Angular: alpha = torque/mass, omega += alpha*dt, dtheta = omega*dt
        val angularAcceleration = accumulatedTorque / mass
        angularVelocity += angularAcceleration * dt
        val rotationDelta = angularVelocity * dt

        // Clear accumulated forces
        accumulatedForce = SceneOffset.Zero
        accumulatedTorque = 0f

        return PhysicsResult(positionDelta, rotationDelta)
    }

    /**
     * Current speed (magnitude of velocity).
     */
    fun speed(): SceneUnit = velocity.length()

    companion object {
        const val MS_TO_SECONDS = 0.001f
        const val SPEED_EPSILON = 0.01f
        const val ANGULAR_EPSILON = 0.001f
    }
}

data class PhysicsResult(
    val positionDelta: SceneOffset,
    val rotationDelta: Float,
)
