package jez.lastfleetprotocol.prototype.components.game.utils

/**
 * A PID (Proportional-Integral-Derivative) controller for smooth feedback control.
 *
 * Produces a control signal from an error value that converges to zero with
 * minimal overshoot and oscillation when tuned correctly.
 *
 * @param kp Proportional gain — reacts to current error magnitude.
 * @param ki Integral gain — corrects accumulated steady-state error.
 * @param kd Derivative gain — dampens based on rate of error change (reduces overshoot).
 * @param integralLimit Clamps the integral term to prevent windup.
 */
class PidController(
    val kp: Float = 1.0f,
    val ki: Float = 0.0f,
    val kd: Float = 0.5f,
    private val integralLimit: Float = 10f,
) {
    private var lastError = 0f
    private var integral = 0f
    private var firstUpdate = true

    /**
     * Compute the control output for the given error.
     *
     * @param error The signed error (target - current). Positive = need to increase.
     * @param dt Time step in seconds. Used to scale integral and derivative terms.
     * @return Control signal to apply (e.g., as a torque multiplier in [-1, 1] range
     *         when the output is clamped by the caller).
     */
    fun update(error: Float, dt: Float): Float {
        val derivative = if (firstUpdate) {
            firstUpdate = false
            0f
        } else {
            if (dt > 0f) (error - lastError) / dt else 0f
        }

        integral += error * dt
        integral = integral.coerceIn(-integralLimit, integralLimit)

        lastError = error

        return kp * error + ki * integral + kd * derivative
    }

    fun reset() {
        lastError = 0f
        integral = 0f
        firstUpdate = true
    }
}
